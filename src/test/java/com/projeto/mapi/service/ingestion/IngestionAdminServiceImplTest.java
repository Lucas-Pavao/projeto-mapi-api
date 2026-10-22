package com.projeto.mapi.service.ingestion;

import com.projeto.mapi.dto.IngestionSourceStatusDTO;
import com.projeto.mapi.repository.ingestion.IngestionRunRepository;
import com.projeto.mapi.service.ingestion.impl.IngestionAdminServiceImpl;
import com.projeto.mapi.service.sensor.SensorFreshnessPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionAdminServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    private SourceRunSummary run(String src, String status, Integer valid, Integer quarantined, Instant lastOk) {
        return new SourceRunSummary(src, UUID.randomUUID(), status, "FAILED".equals(status) ? "boom" : null, valid, quarantined, lastOk, 10);
    }

    @Test
    void derivaStatusPorFonte() {
        List<SourceRunSummary> summaries = List.of(
                new SourceRunSummary("CKAN_RECIFE", null, null, null, null, null, null, 0),
                run("ANA_SOAP", "FAILED", 0, 0, NOW.minusSeconds(60)),
                run("CEMADEN", "OK", 100, 0, NOW.minusSeconds(120)),
                run("APAC_METEO24H", "OK", 5, 0, NOW.minusSeconds(3 * 3600)),
                run("TABUA_MARE", "OK", 0, 12, NOW.minusSeconds(3600)),
                run("OPEN_METEO_ARCHIVE", "OK", 50, 0, NOW.minusSeconds(86400 * 30L)));
        // Subclasse em vez de mock: a classe é concreta e o Mockito inline não suporta o JDK do ambiente.
        IngestionRunRepository repo = new IngestionRunRepository(null, null) {
            @Override
            public List<SourceRunSummary> sourceSummaries(Instant since24h) {
                return summaries;
            }
        };
        var service = new IngestionAdminServiceImpl(repo, new SensorFreshnessPolicy(900_000L, 180_000L, 300_000L),
                Clock.fixed(NOW, ZoneOffset.UTC));

        Map<String, IngestionSourceStatusDTO> by = service.sources().stream()
                .collect(Collectors.toMap(IngestionSourceStatusDTO::source, s -> s));

        assertThat(by.get("CKAN_RECIFE").status()).isEqualTo("IDLE");
        assertThat(by.get("ANA_SOAP").status()).isEqualTo("FAILING");
        assertThat(by.get("ANA_SOAP").lastError()).isEqualTo("boom");
        assertThat(by.get("CEMADEN").status()).isEqualTo("OK");
        assertThat(by.get("CEMADEN").expectedEveryMinutes()).isEqualTo(3);
        assertThat(by.get("APAC_METEO24H").status()).isEqualTo("DELAYED");
        assertThat(by.get("TABUA_MARE").status()).isEqualTo("QUARANTINED");
        assertThat(by.get("OPEN_METEO_ARCHIVE").status()).as("sem ciclo agendado, não há atraso").isEqualTo("OK");
        assertThat(by.get("OPEN_METEO_ARCHIVE").expectedEveryMinutes()).isNull();
    }
}
