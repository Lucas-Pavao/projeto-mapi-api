package com.projeto.mapi.service.ingestion.impl;

import com.projeto.mapi.dto.IngestionSourceStatusDTO;
import com.projeto.mapi.dto.PageResponseDTO;
import com.projeto.mapi.repository.ingestion.IngestionRunRepository;
import com.projeto.mapi.service.ingestion.IngestionAdminService;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.SourceRunSummary;
import com.projeto.mapi.service.sensor.SensorFreshnessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IngestionAdminServiceImpl implements IngestionAdminService {

    private final IngestionRunRepository runs;
    private final SensorFreshnessPolicy freshness;
    private final Clock clock;

    @Override
    public PageResponseDTO<IngestionRunView> listRuns(String source, String status, int page, int size) {
        return new PageResponseDTO<>(page, size, runs.count(source, status),
                runs.page(source, status, size, (long) page * size));
    }

    @Override
    public List<IngestionSourceStatusDTO> sources() {
        Instant now = clock.instant();
        return runs.sourceSummaries(now.minus(Duration.ofHours(24))).stream().map(s -> toDto(s, now)).toList();
    }

    /** Fontes com ciclo agendado: intervalo vem da mesma configuração dos jobs. As demais não têm "atraso". */
    private Integer expectedMinutes(String source) {
        String canonical = Map.of("CEMADEN", "CEMADEN", "APAC_METEO24H", "APAC", "ANA_SOAP", "ANA").get(source);
        return canonical == null ? null : (int) (freshness.expectedIntervalSeconds(canonical) / 60);
    }

    private IngestionSourceStatusDTO toDto(SourceRunSummary s, Instant now) {
        Integer expected = expectedMinutes(s.source());
        String status;
        if (s.lastRunId() == null) {
            status = "IDLE";
        } else if ("FAILED".equals(s.lastStatus())) {
            status = "FAILING";
        } else if (expected != null && (s.lastSuccessAt() == null
                || Duration.between(s.lastSuccessAt(), now).compareTo(Duration.ofMinutes(2L * expected)) > 0)) {
            // antes da quarentena: um run velho que quarentenou tudo não diz que a coleta está viva
            status = "DELAYED";
        } else if (s.lastQuarantined() != null && s.lastQuarantined() > 0
                && (s.lastValid() == null || s.lastValid() == 0)) {
            status = "QUARANTINED";
        } else {
            status = "OK";
        }
        return new IngestionSourceStatusDTO(s.source(), status, expected, s.lastSuccessAt(),
                s.lastRunId() == null ? null : s.lastRunId().toString(), s.lastError(), s.rowsLast24h());
    }
}
