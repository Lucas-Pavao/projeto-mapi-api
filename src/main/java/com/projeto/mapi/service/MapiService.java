package com.projeto.mapi.service;

import com.projeto.mapi.dto.FloodPointRequestDTO;
import com.projeto.mapi.dto.FloodPointResponseDTO;
import com.projeto.mapi.dto.MapiResponseDTO;
import java.util.List;

public interface MapiService {
    MapiResponseDTO getPreciseData(double latitude, double longitude);
    FloodPointResponseDTO createFloodPoint(FloodPointRequestDTO request);
    List<FloodPointResponseDTO> getAllFloodPoints();
    FloodPointResponseDTO getFloodPointBySlug(String slug);
}
