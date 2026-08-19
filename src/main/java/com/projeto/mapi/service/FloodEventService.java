package com.projeto.mapi.service;

import com.projeto.mapi.dto.FloodEventDTO;
import com.projeto.mapi.dto.ScraperEventDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FloodEventService {
    FloodEventDTO reportFlood(FloodEventDTO floodEventDTO);
    FloodEventDTO ingestScraperEvent(ScraperEventDTO scraperEventDTO);
    Page<FloodEventDTO> getHistoryByPoint(String slug, Pageable pageable);
}
