package com.projeto.mapi.service.alerta.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.AlertaProperties;
import com.projeto.mapi.dto.IngestionSourceStatusDTO.Freshness;
import com.projeto.mapi.dto.TerrainResponseDTO;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.alerta.AlertaRepository;
import com.projeto.mapi.repository.alerta.AlertaRepository.State;
import com.projeto.mapi.repository.analise.PointRain10MinRepository;
import com.projeto.mapi.service.alerta.AlertaCycleService;
import com.projeto.mapi.service.alerta.LiveQualityControl;
import com.projeto.mapi.service.alerta.PointBucketClient;
import com.projeto.mapi.service.alerta.PointFloodLevel;
import com.projeto.mapi.service.alerta.PointRain10MinComposer;
import com.projeto.mapi.service.alerta.PointRain10MinComposer.Slot;
import com.projeto.mapi.service.alerta.RainAlertLevel;
import com.projeto.mapi.service.alerta.RainAlertRules;
import com.projeto.mapi.service.alerta.SemDadoMotivo;
import com.projeto.mapi.service.alagamento.FloodForecastClient;
import com.projeto.mapi.service.alagamento.FloodForecastService;
import com.projeto.mapi.service.flood.RainForecastService;
import com.projeto.mapi.service.ingestion.freshness.SourceFreshnessService;
import com.projeto.mapi.service.stream.LiveStreamService;
import com.projeto.mapi.service.terrain.TerrainQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ciclo do alerta por ponto (ADR 0026), separado do ciclo de previsão de alagamento: roda a cada coleta ao vivo da
 * APAC/Cemaden (3 min). Sem estado escondido: o balde roda de novo sobre as últimas 24 h da série de 10 min a cada ciclo.
 * Relógio injetado ({@link Clock}): a reprise roda o mesmo código em tempo simulado.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertaCycleServiceImpl implements AlertaCycleService {

    /** 24 h de passos de 10 min: estado inicial do balde no alerta por regressão. */
    static final int SERIES_STEPS = 144;

    static final double PEAK_LOAD_KM = PointRain10MinComposer.NEAR_KM + PointRain10MinComposer.PEAK_RADIUS_KM;

    private final FloodPointRepository points;
    private final PointRain10MinRepository rainRepo;
    private final AlertaRepository repo;
    private final RainForecastService rainForecast;
    private final PointBucketClient bucket;
    private final TerrainQueryService terrain;
    private final SourceFreshnessService freshness;
    private final LiveStreamService stream;
    private final LiveQualityControl liveQc;
    private final FloodForecastService floodForecast;
    private final AlertaProperties props;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Override
    public synchronized CycleSummary run(String origin) {
        if (!props.isEnabled()) return new CycleSummary(null, 0, 0, List.of());
        UUID cycleId = UUID.randomUUID();
        Instant started = clock.instant();
        repo.insertCycle(cycleId, origin, started);
        boolean sourceStale = sourceStale();
        int done = 0;
        double bucketMs = 0;
        Instant lastSse = null;
        List<String> errors = new ArrayList<>();
        List<State> written = new ArrayList<>();
        for (FloodPoint p : points.findByActiveTrue()) {
            try {
                PointOutcome o = evaluate(cycleId, started, p, sourceStale);
                bucketMs += o.bucketMs();
                Instant sent = stream.publishAlertEvent("alert-state", o.state(), props.isShadow());
                for (Object t : o.transitions()) stream.publishAlertEvent("alert-transition", t, props.isShadow());
                if (o.flood() != null) stream.publishAlertEvent("flood-forecast", o.flood(), floodForecast.shadow());
                if (sent != null) {
                    repo.markSseSent(cycleId, p.getId(), sent);
                    lastSse = sent;
                }
                written.add(o.state());
                done++;
            } catch (Exception e) {
                log.error("Alerta do ponto {}: {}", p.getSlug(), e.toString(), e);
                errors.add(p.getSlug() + ": " + e.getMessage());
            }
        }
        Instant finished = clock.instant();
        repo.finishCycle(cycleId, finished, lastSse, done, Duration.between(started, finished).toMillis(), bucketMs,
                errors.isEmpty() ? null : String.join("; ", errors));
        return new CycleSummary(cycleId, done, bucketMs, written);
    }

    private boolean sourceStale() {
        try {
            Map<String, Freshness> f = freshness.freshness();
            Freshness c = f.get("CEMADEN");
            return c != null && ("STALE".equals(c.state()) || "NO_DATA".equals(c.state()));
        } catch (Exception e) {
            log.warn("Alerta: frescor das fontes indisponível: {}", e.toString());
            return false;
        }
    }

    record PointOutcome(State state, List<Object> transitions, double bucketMs, Object flood) {}

    /** Avalia um ponto. Visível para teste e para a reprise (que confere a série e o balde fora do sistema). */
    PointOutcome evaluate(UUID cycleId, Instant now, FloodPoint p, boolean sourceStale) throws Exception {
        AlertaProperties.Rain rp = props.getRain();
        AlertaProperties.Flood fp = props.getFlood();
        Instant to = now.truncatedTo(ChronoUnit.MINUTES);
        Instant from = to.minus(Duration.ofHours(fp.getSeriesHours()));
        List<PointRain10MinRepository.Block> blocks = rainRepo.blocks(p.getId(), from, to, PEAK_LOAD_KM);
        Map<String, String> month = rainRepo.monthStatus(blocks.stream().map(PointRain10MinRepository.Block::canonicalId)
                .distinct().toList(), from, to);
        PointRain10MinComposer.Result series = PointRain10MinComposer.compose(blocks, month, from, to,
                liveQc.filterFor(p.getId(), blocks, now));
        for (PointRain10MinComposer.Dropped d : series.dropped()) {
            if ("PICO_ISOLADO".equals(d.reason()) || d.reason().startsWith("QC_")) repo.insertQcDrop(d.canonicalId(), d.ts(), d.mm(), d.reason(), now);
        }
        // passos até o da leitura mais nova (o passo seguinte ainda não chegou: não é lacuna)
        List<Slot> slots = series.slots();
        int last = -1;
        if (series.newestReading() != null) {
            Instant newestSlot = PointRain10MinComposerAccess.ceil(series.newestReading());
            for (int k = 0; k < slots.size(); k++) if (!slots.get(k).end().isAfter(newestSlot)) last = k;
        }
        int teSteps = (int) Math.max(1, Math.round(props.getCapacity().getTeMinutes() / PointRain10MinComposer.STEP_MIN));
        int cancelSteps = Math.max(1, rp.getCancelDryMinutes() / PointRain10MinComposer.STEP_MIN);
        Double rainTe = sum(slots, last, teSteps);
        Double rain30 = sum(slots, last, cancelSteps);
        Double lastStep = last >= 0 ? slots.get(last).mm() : null;
        Double rain60 = sum(slots, last, 6);
        RainAlertRules.Measured m = new RainAlertRules.Measured(series.newestReading(), rainTe, rain30, lastStep, sourceStale);

        RainAlertRules.Forecast f = rainForecast.forecastForAlert(p.getId(), p.getSlug(), p.getLatitude(), p.getLongitude());
        Instant aiAt = f.failure() == null ? clock.instant() : null;

        // terreno e balde
        RainAlertRules.TerrainStatus ts;
        TerrainResponseDTO t = null;
        if (fp.getTerrainReview().contains(p.getSlug())) ts = RainAlertRules.TerrainStatus.EM_REVISAO;
        else {
            try {
                t = terrain.findBySlug(p.getSlug());
            } catch (Exception e) {
                t = null;
            }
            boolean ok = t != null && "OK".equals(t.status()) && t.volumeCurve() != null && t.volumeCurve().size() >= 2
                    && t.contributingAreaM2() != null && t.contributingAreaM2() > 0 && t.streetElevationM() != null
                    && t.bottomElevationM() != null;
            ts = ok ? RainAlertRules.TerrainStatus.OK : RainAlertRules.TerrainStatus.SEM_TERRENO;
        }
        RainAlertRules.Bucket b;
        double bucketMs = 0;
        PointBucketClient.Result br = null;
        if (ts != RainAlertRules.TerrainStatus.OK || last < 0) {
            b = RainAlertRules.Bucket.failed(SemDadoMotivo.SERIE_INCOMPLETA);
        } else {
            int start = 0;
            for (int k = 0; k <= last; k++) if (slots.get(k).mm() == null) start = k + 1;
            int n = last - start + 1;
            if (n < fp.getMinCompleteHours() * 6) {
                b = RainAlertRules.Bucket.failed(SemDadoMotivo.SERIE_INCOMPLETA);
            } else {
                List<Double> rain = new ArrayList<>(n);
                for (int k = start; k <= last; k++) rain.add(slots.get(k).mm());
                List<Double> z = t.volumeCurve().stream().map(c -> c.aboveBottomCm() / 100.0).toList();
                List<Double> v = t.volumeCurve().stream().map(TerrainResponseDTO.CurvePoint::cumulativeVolumeM3).toList();
                br = bucket.evaluate(rain, new PointBucketClient.Terrain(p.getSlug(), t.contributingAreaM2(), z, v,
                                t.streetElevationM() - t.bottomElevationM()), props.getCapacity().getTrYears(),
                        props.getCapacity().getTeMinutes(), f.failure() == null ? f.q50_1h() : null,
                        f.failure() == null ? f.q80_1h() : null);
                if (br != null) aiAt = clock.instant();
                if (br == null) b = RainAlertRules.Bucket.failed(SemDadoMotivo.BALDE_INDISPONIVEL);
                else {
                    bucketMs = br.runtimeMs();
                    b = new RainAlertRules.Bucket(br.hNowMm(), br.q50() == null ? null : br.q50().minutesToHcrit(),
                            br.q80() == null ? null : br.q80().minutesToHcrit(), br.q50() == null ? null : br.q50().hMaxMm(),
                            br.q80() == null ? null : br.q80().hMaxMm(), br.version(), null);
                }
            }
        }

        // estação da leitura mais nova a até 5 km (contexto do card e do alerta por regressão)
        PointRain10MinRepository.Block newestBlock = series.newestReading() == null ? null : blocks.stream()
                .filter(x -> x.ts().equals(series.newestReading()) && x.km() <= PointRain10MinComposer.NEAR_KM)
                .findFirst().orElse(null);
        Object flood = floodForecast.enabled()
                ? floodForecast(cycleId, now, p, slots, last, ts, t, rain60, newestBlock) : null;

        State prevState = repo.state(p.getId()).orElse(null);
        RainAlertRules.Previous prev = prevState == null ? RainAlertRules.Previous.initial()
                : new RainAlertRules.Previous(RainAlertLevel.valueOf(prevState.rainAlert()),
                PointFloodLevel.valueOf(prevState.pointFlood()), prevState.rainSince(), prevState.lastRenewedAt(),
                prevState.eventId(), prevState.eventLastActiveAt());
        RainAlertRules.Params params = params();
        RainAlertRules.Decision d = RainAlertRules.decide(now, m, f, b, ts, prev, params);
        double probOn = Double.isNaN(params.probOn()) && f.threshold() != null ? f.threshold() : params.probOn();
        double probOff = Double.isNaN(params.probOff()) ? probOn : params.probOff();

        Map<String, Object> projection = new LinkedHashMap<>();
        if (br != null) {
            projection.put("stepMinutes", 10);
            projection.put("q50", br.q50() == null ? null : br.q50().hMm());
            projection.put("q80", br.q80() == null ? null : br.q80().hMm());
            projection.put("rule", "chuva prevista de 1 h distribuída por igual nos 6 passos");
        }
        List<String> sources = blocks.stream().filter(x -> x.km() <= PointRain10MinComposer.NEAR_KM)
                .map(PointRain10MinRepository.Block::source).distinct().sorted().toList();
        Instant written = clock.instant();
        State st = new State(p.getId(), p.getSlug(), p.getName(), d.rain().name(), name(d.rainReason()), d.flood().name(),
                name(d.floodReason()), d.rainSince(), d.lastRenewedAt(), d.eventId(), d.eventLastActiveAt(), d.trigger(),
                f.probForte1h(), nan(probOn), nan(probOff), rainTe, RainAlertRules.capacityMm(params.trYears(), params.teMinutes()),
                rain30, b.hNowMm(), b.hProjMaxQ50Mm(), b.hProjMaxQ80Mm(), d.floodInMinutes(),
                projection.isEmpty() ? null : mapper.writeValueAsString(projection), f.q50_1h(), f.q80_1h(), f.modelVersion(),
                b.version(), params.trYears(), params.teMinutes(), mapper.writeValueAsString(sources), series.newestReading(),
                d.ageMinutes(), props.isShadow(), cycleId, written);
        repo.upsertState(st);
        List<Object> trs = new ArrayList<>();
        for (RainAlertRules.Transition tr : d.transitions()) {
            repo.insertTransition(p.getId(), now, tr.field(), tr.from(), tr.to(), tr.kind().name(), tr.reason(), d.eventId(),
                    f.probForte1h(), rainTe, b.hNowMm(), b.hProjMaxQ50Mm(), d.floodInMinutes(), props.isShadow(), cycleId);
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("slug", p.getSlug());
            ev.put("at", now.toString());
            ev.put("field", tr.field());
            ev.put("from", tr.from());
            ev.put("to", tr.to());
            ev.put("kind", tr.kind().name());
            ev.put("reason", tr.reason());
            ev.put("eventId", d.eventId() == null ? null : d.eventId().toString());
            ev.put("shadow", props.isShadow());
            trs.add(ev);
        }
        Instant[] receipt = new Instant[]{null, null};
        if (newestBlock != null) receipt = rainRepo.receipt(newestBlock.source(), newestBlock.stationId(), newestBlock.ts());
        repo.insertPointCycle(new AlertaRepository.PointCycle(cycleId, p.getId(), now, d.rain().name(), name(d.rainReason()),
                d.flood().name(), name(d.floodReason()), f.probForte1h(), rainTe, b.hNowMm(), b.hProjMaxQ50Mm(),
                d.floodInMinutes(), d.eventId(), series.newestReading(), receipt[0], receipt[1],
                now, aiAt, written, rain60 != null && rain60 >= props.getLatency().getWetHourMm(), bucketMs, props.isShadow()));
        return new PointOutcome(st, trs, bucketMs, flood);
    }

    /**
     * Alerta de alagamento por regressão (V38): as últimas 24 h da mesma série de 10 min (lacuna vai nula, nunca zero) e o
     * mesmo terreno do balde vão para POST /v1/flood/forecast. Falha aqui não derruba o alerta de chuva.
     */
    private Object floodForecast(UUID cycleId, Instant now, FloodPoint p, List<Slot> slots, int last,
                                 RainAlertRules.TerrainStatus ts, TerrainResponseDTO t, Double rain60,
                                 PointRain10MinRepository.Block newest) {
        try {
            List<Double> s10 = new ArrayList<>();
            if (last >= 0) for (int k = Math.max(0, last - SERIES_STEPS + 1); k <= last; k++) s10.add(slots.get(k).mm());
            FloodForecastClient.Terrain ft = null;
            if (ts == RainAlertRules.TerrainStatus.OK) {
                ft = new FloodForecastClient.Terrain(p.getSlug(), t.contributingAreaM2(),
                        t.volumeCurve().stream().map(c -> c.aboveBottomCm() / 100.0).toList(),
                        t.volumeCurve().stream().map(TerrainResponseDTO.CurvePoint::cumulativeVolumeM3).toList(),
                        t.streetElevationM() - t.bottomElevationM());
            }
            String status = ts == RainAlertRules.TerrainStatus.OK ? "OK"
                    : ts == RainAlertRules.TerrainStatus.EM_REVISAO ? "EM_REVISAO" : "SEM_TERRENO";
            return floodForecast.evaluate(cycleId, now, p, new FloodForecastService.Input(s10, ft, status, rain60,
                    newest == null ? null : newest.canonicalId(), newest == null ? null : newest.km(),
                    props.getCapacity().getTrYears(), props.getCapacity().getTeMinutes()));
        } catch (Exception e) {
            log.error("Alerta por regressão do ponto {}: {}", p.getSlug(), e.toString());
            return null;
        }
    }

    RainAlertRules.Params params() {
        AlertaProperties.Rain rp = props.getRain();
        return new RainAlertRules.Params(rp.getProbOn() == null ? Double.NaN : rp.getProbOn(),
                rp.getProbOff() == null ? Double.NaN : rp.getProbOff(), rp.getMinDurationMinutes(), rp.getExpiryMinutes(),
                rp.getCancelDryMinutes(), rp.getDryMm(), rp.getEventGapMinutes(), props.getFreshnessMinutes(),
                props.getCapacity().getTrYears(), props.getCapacity().getTeMinutes(), props.getFlood().getHcritMm(),
                props.getFlood().getHorizonMinutes());
    }

    private static Double nan(double v) {
        return Double.isNaN(v) ? null : v;
    }

    private static String name(Enum<?> e) {
        return e == null ? null : e.name();
    }

    /** Soma dos {@code n} passos que terminam em {@code last}; nulo se algum deles não tem estação. */
    static Double sum(List<Slot> slots, int last, int n) {
        if (last < 0 || last - n + 1 < 0) return null;
        double s = 0;
        for (int k = last - n + 1; k <= last; k++) {
            Double v = slots.get(k).mm();
            if (v == null) return null;
            s += v;
        }
        return Math.round(s * 10000.0) / 10000.0;
    }

    /** Acesso ao arredondamento do compositor (pacote diferente). */
    static final class PointRain10MinComposerAccess {
        static Instant ceil(Instant t) {
            long s = t.getEpochSecond();
            long step = PointRain10MinComposer.STEP_MIN * 60L;
            long r = Math.floorMod(s, step);
            return r == 0 ? Instant.ofEpochSecond(s) : Instant.ofEpochSecond(s - r + step);
        }
    }
}
