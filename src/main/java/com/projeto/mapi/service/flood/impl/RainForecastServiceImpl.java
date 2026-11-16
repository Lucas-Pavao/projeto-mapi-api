package com.projeto.mapi.service.flood.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.dto.RainForecastAccuracyDTO;
import com.projeto.mapi.dto.RainForecastDTO;
import com.projeto.mapi.repository.analise.RainForecastRepository;
import com.projeto.mapi.repository.analise.RainForecastRepository.OmHour;
import com.projeto.mapi.repository.analise.RainForecastRepository.Reading;
import com.projeto.mapi.repository.analise.RainForecastRepository.Stored;
import com.projeto.mapi.service.flood.RainForecastClient;
import com.projeto.mapi.service.flood.RainForecastService;
import com.projeto.mapi.service.weather.OpenMeteoForecastClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RainForecastServiceImpl implements RainForecastService {

    /** O serviço de IA precisa de 6 h de leitura; 30 min a mais cobrem o bloco que termina na borda. */
    static final Duration LOOKBACK = Duration.ofMinutes(390);
    static final int MAX_HORIZON_H = 6;
    /** Corte FORTE do ADR 0017 (docs/EVENTOS-DE-CHUVA.md), mm/h de pico horário. */
    static final double FORTE_MM_H = 25.15;
    /** A PED chega com até ~1 h de atraso: só confere a previsão 2 h depois de vencida. */
    static final long SETTLE_MINUTES = 120;

    private final RainForecastRepository repo;
    private final RainForecastClient client;
    private final OpenMeteoForecastClient openMeteo;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Value("${mapi.rain-forecast.enabled:true}")
    private boolean enabled;
    @Value("${mapi.rain-forecast.open-meteo-enabled:true}")
    private boolean openMeteoEnabled;

    @Override
    public Optional<RainForecastDTO> forecastAndStore(long floodPointId, String slug, double lat, double lon) {
        if (!enabled) return Optional.empty();
        try {
            Instant now = clock.instant();
            List<Reading> readings = repo.neighborReadings(floodPointId, now.minus(LOOKBACK), now);
            if (readings.isEmpty()) {
                log.warn("Previsão de chuva do ponto {}: nenhuma leitura nas últimas 6 h; não emitida", slug);
                return Optional.empty();
            }
            Instant latest = readings.get(readings.size() - 1).ts();
            List<OmHour> om = openMeteoHours(floodPointId, slug, lat, lon, latest);
            Optional<RainForecastClient.Result> res = client.forecast(lat, lon, readings, om);
            if (res.isEmpty()) return Optional.empty();
            RainForecastClient.Result r = res.get();
            String sources = mapper.writeValueAsString(r.sources());
            String missing = mapper.writeValueAsString(r.missingInputs());
            for (RainForecastClient.Horizon h : r.horizons()) {
                repo.insert(new Stored(floodPointId, slug, r.issuedAt(), h.horizonHours(), h.validUntil(), h.q50(), h.q80(),
                        h.q95(), h.probMedia(), h.probForte(), h.forteAlert(), r.modelVersion(), h.model(), sources, missing,
                        r.latestReadingUtc(), r.latestReadingAgeMinutes()));
            }
            return Optional.of(new RainForecastDTO(floodPointId, slug, r.issuedAt(), r.modelVersion(), r.sources(),
                    r.missingInputs(), r.latestReadingUtc(), r.latestReadingAgeMinutes(), r.horizons().stream()
                    .map(h -> new RainForecastDTO.Horizon(h.horizonHours(), h.validUntil(), h.q50(), h.q80(), h.q95(),
                            h.probMedia(), h.probForte(), h.forteAlert(), h.model())).toList(), RainForecastDTO.ATTRIBUTION));
        } catch (Exception e) {
            log.error("Falha na previsão de chuva do ponto {}: {}", slug, e.toString());
            return Optional.empty();
        }
    }

    /**
     * Horas do Open-Meteo que cobrem (t, t+6h], só as emitidas até t. Busca na fonte quando faltam; falha da fonte não
     * impede a previsão: as entradas vão ausentes e a IA as declara em missing_inputs.
     */
    private List<OmHour> openMeteoHours(long floodPointId, String slug, double lat, double lon, Instant t) {
        if (!openMeteoEnabled) return List.of();
        Instant from = t.truncatedTo(ChronoUnit.HOURS);
        Instant to = from.plus(Duration.ofHours(MAX_HORIZON_H + 1));
        List<OmHour> have = repo.omHours(floodPointId, t, from, to);
        if (have.size() >= MAX_HORIZON_H + 1) return have;
        try {
            OpenMeteoForecastClient.Batch b = openMeteo.previousDayRun(lat, lon);
            for (OpenMeteoForecastClient.Hour h : b.hours()) {
                if (h.mm() != null) repo.upsertOmHour(floodPointId, h.hourEndUtc(), h.mm(), b.gridLat(), b.gridLon(), b.rawHash());
            }
        } catch (Exception e) {
            log.warn("Open-Meteo (rodada da véspera) indisponível para o ponto {}: {}", slug, e.toString());
        }
        return repo.omHours(floodPointId, t, from, to);
    }

    @Override
    public List<RainForecastDTO> latest() {
        Map<Long, List<Stored>> byPoint = new LinkedHashMap<>();
        for (Stored s : repo.latest()) byPoint.computeIfAbsent(s.floodPointId(), k -> new ArrayList<>()).add(s);
        List<RainForecastDTO> out = new ArrayList<>();
        for (List<Stored> rows : byPoint.values()) {
            Stored f = rows.get(0);
            out.add(new RainForecastDTO(f.floodPointId(), f.slug(), f.issuedAt(), f.modelVersion(), list(f.sourcesJson()),
                    list(f.missingJson()), f.latestReadingUtc(), f.latestReadingAgeMin(), rows.stream()
                    .map(h -> new RainForecastDTO.Horizon(h.horizonHours(), h.validUntil(), h.q50(), h.q80(), h.q95(),
                            h.probMedia(), h.probForte(), h.forteAlert(), h.model())).toList(), RainForecastDTO.ATTRIBUTION));
        }
        return out;
    }

    private List<String> list(String json) {
        try {
            return json == null ? List.of() : mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Conferência diária (03:30 UTC) e uma hora depois da subida. É o único número do sistema que se atualiza com dado real. */
    @Scheduled(cron = "${mapi.rain-forecast.verify-cron:0 30 3 * * *}", zone = "UTC")
    @Scheduled(initialDelayString = "${mapi.rain-forecast.verify-initial-delay-ms:3600000}", fixedDelay = Long.MAX_VALUE)
    public void scheduledVerify() {
        if (!enabled) return;
        try {
            int n = verifyPending();
            log.info("Previsão de chuva: {} linha(s) conferida(s) contra a chuva medida", n);
        } catch (Exception e) {
            log.error("Falha na conferência da previsão de chuva: {}", e.toString());
        }
    }

    @Override
    public int verifyPending() {
        int total = 0, n;
        do {
            n = repo.verifyPending(clock.instant(), SETTLE_MINUTES, FORTE_MM_H);
            total += n;
        } while (n >= 20000);
        return total;
    }

    @Override
    public RainForecastAccuracyDTO accuracy(int days) {
        Instant to = clock.instant();
        Instant from = to.minus(Duration.ofDays(days));
        return new RainForecastAccuracyDTO(days, from, to, repo.lastVerifiedAt(), repo.pending(),
                repo.accuracy(from, to, FORTE_MM_H), RainForecastDTO.ATTRIBUTION);
    }
}
