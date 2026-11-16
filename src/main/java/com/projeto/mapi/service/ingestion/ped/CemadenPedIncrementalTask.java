package com.projeto.mapi.service.ingestion.ped;

import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.dto.PointRainRebuildDTO;
import com.projeto.mapi.repository.ingestion.ObservationReadRepository;
import com.projeto.mapi.service.analise.PointRainService;
import com.projeto.mapi.service.ingestion.IngestionKind;
import com.projeto.mapi.service.ingestion.IngestionMode;
import com.projeto.mapi.service.ingestion.IngestionPipelineService;
import com.projeto.mapi.service.ingestion.IngestionRequest;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.adapter.ped.CemadenPedAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carga periódica da PED (ADR 0021, emenda de 2026-11-16). A carga histórica parou em 2026-10-07 e a série de chuva dos
 * pontos (ADR 0022) parou junto. A cada ciclo:
 * <ol>
 *   <li>baixa a série de 10 min de (última leitura PED gravada − sobreposição, hora cheia atual], pelo pipeline de
 *   ingestão (staging, quarentena, consolidação versionada; leitura igual não vira nova versão);</li>
 *   <li>recalcula a checagem mensal, o vínculo e a série horária dos pontos desde o dia 1 do primeiro mês tocado
 *   (a checagem é por mês inteiro: recalcular só a cauda trocaria a checagem do mês por uma de poucas horas).</li>
 * </ol>
 * A checagem do mês corrente é provisória e muda a cada ciclo até o mês fechar. Roda no {@code taskExecutor} e divide o
 * limite da PED (uma requisição a cada 6 s) com os acumulados recentes, pelo mesmo cliente.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CemadenPedIncrementalTask {

    private final IngestionProperties props;
    private final IngestionPipelineService pipeline;
    private final PointRainService pointRain;
    private final ObservationReadRepository observations;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Clock clock = Clock.systemUTC();

    /** Só para teste. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Async("taskExecutor")
    @Scheduled(fixedDelayString = "${mapi.ingestion.cemaden-ped.incremental-delay-ms:3600000}",
            initialDelayString = "${mapi.ingestion.cemaden-ped.incremental-initial-delay-ms:300000}")
    public void scheduledRun() {
        IngestionProperties.CemadenPed cfg = props.getCemadenPed();
        if (!cfg.isIncrementalEnabled() || cfg.getEmail() == null || cfg.getEmail().isBlank()) return;
        if (!running.compareAndSet(false, true)) return;
        try {
            runOnce();
        } catch (RuntimeException e) {
            log.warn("PED periódica: ciclo falhou, tenta de novo no próximo: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /** Uma volta: janela a baixar, carga e recálculo. Vazio se não havia nada a baixar ou a carga falhou. */
    public Optional<PointRainRebuildDTO> runOnce() {
        IngestionProperties.CemadenPed cfg = props.getCemadenPed();
        Instant now = clock.instant();
        Instant to = now.truncatedTo(ChronoUnit.HOURS);
        Instant floor = to.minus(Duration.ofDays(cfg.getIncrementalMaxCatchUpDays()));
        Instant last = observations.latestTs(CemadenPedAdapter.SOURCE, "precipitation", floor).orElse(floor);
        Instant from = last.minus(Duration.ofHours(cfg.getIncrementalOverlapHours())).truncatedTo(ChronoUnit.HOURS);
        if (from.isBefore(floor)) from = floor;
        if (!from.isBefore(to)) return Optional.empty();

        log.info("PED periódica: baixando [{}, {}) (última leitura gravada {})", from, to, last);
        IngestionRunView run = pipeline.execute(new IngestionRequest(CemadenPedAdapter.SOURCE, from, to,
                IngestionMode.REFETCH, IngestionKind.BACKFILL));
        // OK ou PARTIAL (alguma janela falhou): recalcula com o que chegou; o próximo ciclo cobre o resto.
        if ("FAILED".equals(run.status())) {
            log.warn("PED periódica: run {} terminou {}: {}", run.id(), run.status(), run.errorSummary());
            return Optional.empty();
        }
        Instant monthStart = YearMonth.from(from.atZone(ZoneOffset.UTC)).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        PointRainRebuildDTO rebuilt = pointRain.rebuild(monthStart, to);
        log.info("PED periódica: run {} {} (obs novas {}, atualizadas {}); chuva dos pontos recalculada de {} a {}",
                run.id(), run.status(), run.obsInserted(), run.obsUpdated(), monthStart, to);
        return Optional.of(rebuilt);
    }
}
