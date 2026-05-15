package com.projeto.mapi.service;

import com.projeto.mapi.dto.TideTableResponseDTO;
import java.io.IOException;
import java.util.List;

public interface NavyScraperService {
    /**
     * Scrapes the Navy website for Pernambuco tide tables and saves them.
     * @param year The year to search for (optional, defaults to current year)
     * @return List of saved TideTable DTOs
     */
    List<TideTableResponseDTO> scrapeAndIngestPernambuco(Integer year) throws IOException;

    /**
     * Ingests tide tables from provided HTML content.
     * Useful when the automatic scraper is blocked by Cloudflare.
     * @param html The HTML content of the Navy page
     * @param year The year of the tide tables
     * @return List of saved TideTable DTOs
     */
    List<TideTableResponseDTO> ingestFromHtml(String html, Integer year) throws IOException;
}
