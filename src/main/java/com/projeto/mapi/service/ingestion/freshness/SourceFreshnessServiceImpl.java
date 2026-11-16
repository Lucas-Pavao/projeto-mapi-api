package com.projeto.mapi.service.ingestion.freshness;

import com.projeto.mapi.dto.IngestionSourceStatusDTO.Freshness;
import com.projeto.mapi.mapper.AlertMapper;
import com.projeto.mapi.model.Alert;
import com.projeto.mapi.model.AlertKind;
import com.projeto.mapi.repository.AlertRepository;
import com.projeto.mapi.repository.ingestion.SourceFreshnessRepository;
import com.projeto.mapi.service.alert.AlertCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Compara a leitura ACEITA mais nova de cada fonte com o limite do source_registry (V29). Fonte que roda e não aceita
 * nada (tudo em quarentena) conta como parada: o critério é leitura em {@code observation}, não execução.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SourceFreshnessServiceImpl implements SourceFreshnessService {

    /** Fontes que entram no modelo de alagamento: alerta CRITICAL. As demais, WARNING. */
    static final Set<String> FEEDS_MODEL = Set.of("CEMADEN", "CEMADEN_PED", "TABUAMARE_PE02", "TIDE_PE02_HOURLY");

    private final SourceFreshnessRepository repo;
    private final AlertRepository alerts;
    private final AlertMapper alertMapper;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    @Override
    public Map<String, Freshness> freshness() {
        Instant now = clock.instant();
        Map<String, Freshness> out = new LinkedHashMap<>();
        for (SourceFreshnessRepository.Limits l : repo.limits()) {
            if (l.maxAgeMinutes() == null && l.minCoverageAheadMinutes() == null) {
                out.put(l.source(), new Freshness("UNMONITORED", null, null, null, null, null));
                continue;
            }
            out.put(l.source(), evaluate(l, repo.readings(l.source(), now), now));
        }
        return out;
    }

    static Freshness evaluate(SourceFreshnessRepository.Limits l, SourceFreshnessRepository.Readings r, Instant now) {
        Long age = r.lastAccepted() == null ? null : Duration.between(r.lastAccepted(), now).toMinutes();
        String state;
        if (l.minCoverageAheadMinutes() != null) {
            boolean low = r.lastAhead() == null
                    || Duration.between(now, r.lastAhead()).toMinutes() < l.minCoverageAheadMinutes();
            state = r.lastAhead() == null ? "NO_DATA" : low ? "COVERAGE_LOW" : "FRESH";
        } else if (r.lastAccepted() == null) {
            state = "NO_DATA";
        } else {
            state = age > l.maxAgeMinutes() ? "STALE" : "FRESH";
        }
        return new Freshness(state, r.lastAccepted(), age, l.maxAgeMinutes(), r.lastAhead(), l.minCoverageAheadMinutes());
    }

    @Scheduled(fixedDelayString = "${mapi.ingestion.freshness-check-ms:600000}",
            initialDelayString = "${mapi.ingestion.freshness-initial-delay-ms:300000}")
    public void scheduled() {
        try {
            int n = raiseAlerts();
            if (n > 0) log.warn("Fonte velha: {} alerta(s) aberto(s)", n);
        } catch (RuntimeException e) {
            log.warn("Checagem de fonte velha falhou: {}", e.getMessage());
        }
    }

    @Override
    public int raiseAlerts() {
        Instant now = clock.instant();
        int opened = 0;
        for (Map.Entry<String, Freshness> e : freshness().entrySet()) {
            String source = e.getKey();
            Freshness f = e.getValue();
            if (!Set.of("STALE", "NO_DATA", "COVERAGE_LOW").contains(f.state())) continue;
            // Um alerta por episódio: nada de novo se já houve alerta depois da última leitura aceita e nas últimas 24 h.
            Instant since = now.minus(Duration.ofHours(24));
            if (f.lastAcceptedAt() != null && f.lastAcceptedAt().isAfter(since) && !"COVERAGE_LOW".equals(f.state())) {
                since = f.lastAcceptedAt();
            }
            if (repo.alertedSince(source, since)) continue;
            Alert alert = alerts.save(Alert.builder()
                    .kind(AlertKind.SOURCE_STALE)
                    .severity(FEEDS_MODEL.contains(source) ? Alert.Severity.CRITICAL : Alert.Severity.WARNING)
                    .stationId(source)
                    .title(title(source, f))
                    .detail(detail(f))
                    .observedValue(f.ageMinutes() == null ? null : f.ageMinutes().doubleValue())
                    .triggeredAt(now).build());
            publisher.publishEvent(new AlertCreatedEvent(alertMapper.toDto(alert)));
            opened++;
        }
        return opened;
    }

    static String title(String source, Freshness f) {
        return switch (f.state()) {
            case "COVERAGE_LOW" -> "Tábua de maré acaba em menos do que o mínimo: " + source;
            case "NO_DATA" -> "Fonte sem nenhuma leitura aceita em 60 dias: " + source;
            default -> "Fonte parada: " + source + " sem leitura aceita há " + f.ageMinutes() + " min";
        };
    }

    static String detail(Freshness f) {
        if ("COVERAGE_LOW".equals(f.state())) {
            return "Cobertura até " + f.coverageUntil() + "; mínimo de " + f.minCoverageAheadMinutes() / 1440 + " dias à frente.";
        }
        return "Última leitura aceita: " + (f.lastAcceptedAt() == null ? "nenhuma" : f.lastAcceptedAt())
                + "; idade máxima aceita: " + f.maxAgeMinutes() + " min. Leitura em quarentena não conta.";
    }
}
