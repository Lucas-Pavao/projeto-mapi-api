package com.projeto.mapi.service;

import com.projeto.mapi.dto.FloodEventDTO;
import com.projeto.mapi.dto.ScraperEventDTO;
import java.util.List;

public interface FloodEventService {
    FloodEventDTO reportFlood(FloodEventDTO floodEventDTO);
    FloodEventDTO ingestScraperEvent(ScraperEventDTO scraperEventDTO);
    List<FloodEventDTO> getHistoryByPoint(String slug);
}
