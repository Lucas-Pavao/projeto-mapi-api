package com.projeto.mapi.service.scheduler;

import com.projeto.mapi.service.NavyScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class TideScheduler {

    private final NavyScraperService navyScraperService;

    /**
     * Runs the scraping and ingestion process for Pernambuco tide tables.
     * Scheduled to run at midnight on the first day of every second month.
     * (January, March, May, July, September, November)
     */
    @Scheduled(cron = "0 0 0 1 1/2 *")
    public void scheduleTideUpdate() {
        log.info("Executando atualização agendada das tábuas de maré...");
        try {
            navyScraperService.scrapeAndIngestPernambuco(null);
            log.info("Atualização agendada concluída com sucesso.");
        } catch (IOException e) {
            log.error("Erro durante a atualização agendada das tábuas de maré: {}", e.getMessage());
        }
    }
}
