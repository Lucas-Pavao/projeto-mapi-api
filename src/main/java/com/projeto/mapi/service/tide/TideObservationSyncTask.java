package com.projeto.mapi.service.tide;

import com.projeto.mapi.repository.ingestion.ObservationReadRepository;
import com.projeto.mapi.service.ingestion.IngestionKind;
import com.projeto.mapi.service.ingestion.IngestionMode;
import com.projeto.mapi.service.ingestion.IngestionPipelineService;
import com.projeto.mapi.service.ingestion.IngestionRequest;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.adapter.tide.TabuaMareApiAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Mantém os extremos da maré prevista do Porto do Recife em {@code observation} (fonte TABUAMARE_PE02), que é de onde
 * a linha do tempo de Eventos e o cartão "Chuva e maré hoje" leem. {@link TideTableSyncTask} só popula a tábua local
 * ({@code tide_tables}) da tela Maré; sem este job, um banco recriado fica sem maré na timeline até alguém disparar a
 * carga à mão. A checagem é uma query local; só chama a TabuaMare quando falta cobertura.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TideObservationSyncTask {

    static final ZoneId LOCAL = ZoneId.of("America/Recife");
    /** Dias locais carregados antes de hoje e depois de hoje. */
    static final int DAYS_BACK = 3;
    static final int DAYS_AHEAD = 10;
    /** Cobertura mínima para não recarregar: um extremo nas últimas 12 h e outro a pelo menos 3 dias à frente. */
    static final Duration NEED_BACK = Duration.ofHours(12);
    static final Duration NEED_AHEAD = Duration.ofDays(3);

    private final IngestionPipelineService pipeline;
    private final ObservationReadRepository observations;
    private final Clock clock;

    @Scheduled(fixedRateString = "${app.tide.observation-sync-ms:21600000}",
            initialDelayString = "${app.tide.observation-sync-initial-delay-ms:60000}")
    public void syncIfMissing() {
        MDC.put("requestId", "tide-obs-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            Instant now = clock.instant();
            if (covered(now)) return;
            LocalDate today = LocalDate.ofInstant(now, LOCAL);
            LocalDate first = today.minusDays(DAYS_BACK);
            // A TabuaMare só serve o ano corrente: a janela não passa de 31/12 (o adaptador falharia no ano seguinte).
            LocalDate last = today.plusDays(DAYS_AHEAD);
            if (last.getYear() != today.getYear()) last = LocalDate.of(today.getYear(), 12, 31);
            Instant from = first.atStartOfDay(LOCAL).toInstant();
            Instant to = last.plusDays(1).atStartOfDay(LOCAL).toInstant();
            IngestionRunView run = pipeline.execute(new IngestionRequest(TabuaMareApiAdapter.SOURCE, from, to,
                    IngestionMode.REFETCH, IngestionKind.LIVE));
            log.info("Maré prevista em observation recarregada: run {} {} ({} a {}, {} novas)", run.id(), run.status(),
                    first, last, run.obsInserted());
        } catch (Exception e) {
            log.error("Falha ao recarregar a maré prevista em observation: {}", e.getMessage(), e);
        } finally {
            MDC.remove("requestId");
        }
    }

    boolean covered(Instant now) {
        List<ObservationReadRepository.TideExtremeRow> rows =
                observations.tideExtremes(now.minus(NEED_BACK), now.plus(NEED_AHEAD).plus(Duration.ofDays(1)));
        if (rows.isEmpty()) return false;
        return !rows.get(0).tsUtc().isAfter(now)
                && !rows.get(rows.size() - 1).tsUtc().isBefore(now.plus(NEED_AHEAD));
    }
}
