package com.projeto.mapi.service.flood.impl;

import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.repository.analise.PointRainRepository;
import com.projeto.mapi.repository.analise.PointRainRepository.HourRow;
import com.projeto.mapi.repository.analise.PointRainRepository.MonthCheckRow;
import com.projeto.mapi.repository.ingestion.ObservationReadRepository.HourlyRainRow;
import com.projeto.mapi.repository.station.StationCanonicalRepository;
import com.projeto.mapi.repository.station.StationCanonicalRepository.CanonicalView;
import com.projeto.mapi.service.analise.PointRainComposer;
import com.projeto.mapi.service.analise.PointRainService;
import com.projeto.mapi.service.flood.PointRainInputService;
import com.projeto.mapi.service.flood.PointRainInputs;
import com.projeto.mapi.service.sensor.RainSpatialCheck;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chuva da previsão ao vivo pela série medida (ADR 0007, emenda 3), com a regra do treino (ADR 0022, chuva-ponto-v4):
 *
 * <ol>
 *   <li>estações: as canônicas ancoradas na PED (as mesmas do treino). Estação só da coleta ao vivo não entra: não
 *   tem checagem mensal e o modelo nunca a viu;</li>
 *   <li>valor da hora por estação: PED se houver; senão a coleta ao vivo da mesma estação física (ADR 0023). A hora
 *   mais recente vem do ao vivo, que chega antes;</li>
 *   <li>pico isolado sai da hora ({@link RainSpatialCheck}), como na série do ponto;</li>
 *   <li>estação reprovada (FAIL) ou sem dado (OFFLINE) no mês da hora fica fora. Mês ainda sem checagem usa a do mês
 *   anterior; sem nenhuma, a estação conta como válida (UNCHECKED);</li>
 *   <li>hora do ponto = maior valor entre as válidas a até 5 km ({@link PointRainComposer}); sem valor, a hora é
 *   lacuna. Acumulado de N h com qualquer hora em lacuna é ausente, como em {@code point_rain_accum_v}.</li>
 * </ol>
 *
 * O retrato da rede (25 h de todas as estações) custa uma consulta por série e é reaproveitado entre pontos e ciclos.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PointRainInputServiceImpl implements PointRainInputService {

    static final String PED = "CEMADEN_PED";
    static final String LIVE = "CEMADEN";
    /** 24 h do maior acumulado + 1 h para a referência recuar (tolerância de latência). */
    static final int HOURS = 25;
    static final int[] WINDOWS = {1, 3, 6, 12, 24};
    static final String[] FEATURES = {"current_rainfall", "rainfall_3h_accumulated", "rainfall_6h_accumulated",
            "rainfall_12h_accumulated", "rainfall_24h_accumulated"};

    private final StationCanonicalRepository canonicals;
    private final PointRainRepository rain;
    private final IngestionProperties props;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.projeto.mapi.config.PointRainProperties pointRainProps;

    /** Série de uma estação no retrato: i = hora com fim em {@code from + (i+1) h}; NaN = sem valor. */
    record St(String id, String name, double lat, double lon, double[] mm, String[] src, boolean[] valid) {}

    record Snapshot(Instant hourEnd, Instant builtAt, List<St> stations, Map<String, Instant> lastReading) {}

    private volatile Snapshot cache;

    /** Descarta o retrato (testes e recarga manual). */
    public void invalidate() {
        cache = null;
    }

    @Override
    public PointRainInputs inputsAt(double lat, double lon, Instant now) {
        Instant hourEnd = now.truncatedTo(ChronoUnit.HOURS);
        Snapshot snap = snapshot(hourEnd, now);
        double radius = props.getSensorLinkageRadiusM();

        List<St> cands = new ArrayList<>();
        List<Double> dists = new ArrayList<>();
        for (St s : snap.stations()) {
            double d = GeoUtils.calculateDistance(lat, lon, s.lat(), s.lon()) * 1000.0;
            if (d > radius) continue;
            cands.add(s);
            dists.add(d);
        }
        Double[] hourValue = new Double[HOURS];
        St[] hourStation = new St[HOURS];
        Double[] hourDistance = new Double[HOURS];
        int[] nValid = new int[HOURS];
        for (int i = 0; i < HOURS; i++) {
            List<PointRainComposer.Candidate> cs = new ArrayList<>();
            for (int j = 0; j < cands.size(); j++) {
                St s = cands.get(j);
                cs.add(new PointRainComposer.Candidate(s.id(), dists.get(j), s.valid()[i], s.mm()[i], 0, Double.NaN));
            }
            PointRainComposer.Hour h = PointRainComposer.compose(cs);
            hourValue[i] = h.value();
            hourDistance[i] = h.distanceM();
            nValid[i] = h.nValid();
            if (h.stationId() != null) {
                hourStation[i] = cands.stream().filter(s -> s.id().equals(h.stationId())).findFirst().orElse(null);
            }
        }

        // Hora de referência: a última fechada; se ainda não chegou nada dela, a anterior (dentro da tolerância).
        int ref = HOURS - 1;
        Instant refEnd = hourEnd;
        long sinceClose = Duration.between(hourEnd, now).toMinutes();
        if (hourValue[ref] == null && sinceClose < props.getPredictionRain().getLatencyGraceMinutes()) {
            ref--;
            refEnd = hourEnd.minus(1, ChronoUnit.HOURS);
        }

        List<PointRainInputs.Value> values = new ArrayList<>();
        Double[] acc = new Double[WINDOWS.length];
        for (int w = 0; w < WINDOWS.length; w++) {
            int n = WINDOWS[w], with = 0;
            double sum = 0;
            Map<String, PointRainInputs.Station> used = new LinkedHashMap<>();
            for (int i = ref - n + 1; i <= ref; i++) {
                if (hourValue[i] == null) continue;
                with++;
                sum += hourValue[i];
                St s = hourStation[i];
                if (s != null) used.putIfAbsent(s.id() + "|" + s.src()[i], new PointRainInputs.Station(s.id(), s.name(), s.src()[i]));
            }
            acc[w] = with == n ? Math.round(sum * 100.0) / 100.0 : null;
            values.add(new PointRainInputs.Value(FEATURES[w], acc[w], n, with, List.copyOf(used.values())));
        }

        Instant latest = null;
        for (St s : cands) {
            if (!s.valid()[ref]) continue;
            Instant t = snap.lastReading().get(s.id());
            if (t != null && !t.isAfter(now) && (latest == null || t.isAfter(latest))) latest = t;
        }
        double primaryM = (pointRainProps != null ? pointRainProps.getPrimaryRadiusKm() : 3.0) * 1000.0;
        double maxM = (pointRainProps != null ? pointRainProps.getMaxRadiusKm() : 5.0) * 1000.0;
        double distantConf = pointRainProps != null ? pointRainProps.getDistantStationConfidence() : 0.85;
        double reanalConf = pointRainProps != null ? pointRainProps.getReanalysisConfidence() : 0.60;
        PointRainComposer.RainProvenance prov = PointRainComposer.classifyProvenance(
                hourValue[ref], hourDistance[ref], primaryM, maxM, false, distantConf, reanalConf);
        List<String> flags = prov != null && prov.flag() != null ? List.of(prov.flag()) : List.of();
        return new PointRainInputs(PointRainInputs.MEASURED, refEnd, acc[0], acc[1], acc[2], acc[3], acc[4], values,
                latest, nValid[ref],
                prov == null ? null : prov.tier().name(),
                prov == null ? null : prov.confidence(),
                flags);
    }

    private Snapshot snapshot(Instant hourEnd, Instant now) {
        Snapshot c = cache;
        long ttl = props.getPredictionRain().getSnapshotTtlSeconds();
        if (c != null && c.hourEnd().equals(hourEnd) && Duration.between(c.builtAt(), now).getSeconds() < ttl
                && !now.isBefore(c.builtAt())) {
            return c;
        }
        synchronized (this) {
            c = cache;
            if (c != null && c.hourEnd().equals(hourEnd) && Duration.between(c.builtAt(), now).getSeconds() < ttl
                    && !now.isBefore(c.builtAt())) {
                return c;
            }
            cache = build(hourEnd, now);
            return cache;
        }
    }

    private Snapshot build(Instant hourEnd, Instant now) {
        long t0 = System.currentTimeMillis();
        Instant from = hourEnd.minus(HOURS, ChronoUnit.HOURS);
        List<St> stations = new ArrayList<>();
        for (CanonicalView c : canonicals.canonicals()) {
            if (!PED.equals(c.primarySource())) continue;
            double[] mm = new double[HOURS];
            String[] src = new String[HOURS];
            Arrays.fill(mm, Double.NaN);
            fill(mm, src, LIVE, aliasId(c, LIVE), from, hourEnd);            // ao vivo primeiro...
            fill(mm, src, PED, aliasId(c, PED), from, hourEnd);              // ...a PED sobrescreve (vence na mesma hora)
            stations.add(new St(c.canonicalId(), c.name(), c.lat(), c.lon(), mm, src, new boolean[HOURS]));
        }

        // pico isolado, hora a hora, entre todas as estações do retrato (mesma regra da série do ponto)
        for (int i = 0; i < HOURS; i++) {
            Instant h = from.plus(i + 1L, ChronoUnit.HOURS);
            List<HourlyRainRow> rows = new ArrayList<>();
            boolean suspect = false;
            for (St s : stations) {
                if (Double.isNaN(s.mm()[i])) continue;
                rows.add(new HourlyRainRow(PED, s.id(), h, s.mm()[i], s.lat(), s.lon()));
                suspect |= s.mm()[i] >= RainSpatialCheck.SUSPECT_MM;
            }
            if (!suspect) continue;
            List<St> drop = new ArrayList<>();
            for (St s : stations) {
                if (Double.isNaN(s.mm()[i]) || s.mm()[i] < RainSpatialCheck.SUSPECT_MM) continue;
                HourlyRainRow self = new HourlyRainRow(PED, s.id(), h, s.mm()[i], s.lat(), s.lon());
                if (RainSpatialCheck.isIsolatedPeak(self, rows)) drop.add(s);
            }
            for (St s : drop) {
                s.mm()[i] = Double.NaN;
                s.src()[i] = null;
            }
        }

        // checagem do mês da hora; mês ainda sem checagem usa a do mês anterior; sem nenhuma, válida
        Instant firstMonth = monthStart(from.plusSeconds(1)).atZone(ZoneOffset.UTC).minusMonths(1).toInstant();
        Instant afterLast = monthStart(hourEnd.minusNanos(1)).atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        Map<String, String> status = new HashMap<>();
        for (MonthCheckRow r : rain.monthChecks(PointRainService.SOURCE, PointRainService.RULE_VERSION, firstMonth, afterLast)) {
            status.put(r.stationId() + "|" + r.monthStart(), r.status());
        }
        for (St s : stations) {
            for (int i = 0; i < HOURS; i++) {
                Instant m = monthStart(from.plus(i + 1L, ChronoUnit.HOURS).minusNanos(1));
                String st = status.get(s.id() + "|" + m);
                if (st == null) st = status.get(s.id() + "|" + m.atZone(ZoneOffset.UTC).minusMonths(1).toInstant());
                s.valid()[i] = st == null || "PASS".equals(st) || "UNCHECKED".equals(st);
            }
        }
        Map<String, Instant> last = canonicals.lastReadingByCanonical(from);
        log.info("Chuva da previsão: retrato de {} estações PED até {} em {} ms", stations.size(), hourEnd,
                System.currentTimeMillis() - t0);
        return new Snapshot(hourEnd, now, stations, last);
    }

    private void fill(double[] mm, String[] src, String source, String stationId, Instant from, Instant to) {
        if (stationId == null) return;
        for (HourRow h : rain.hourly(source, stationId, from, to)) {
            int i = (int) Duration.between(from, h.hourEndUtc()).toHours() - 1;
            if (i < 0 || i >= mm.length || h.value() == null) continue;
            mm[i] = h.value();
            src[i] = source;
        }
    }

    private static String aliasId(CanonicalView c, String source) {
        String prefix = source + ":";
        return c.aliases().stream().filter(a -> a.startsWith(prefix)).map(a -> a.substring(prefix.length())).findFirst()
                .orElse(null);
    }

    private static Instant monthStart(Instant t) {
        return YearMonth.from(t.atZone(ZoneOffset.UTC)).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
