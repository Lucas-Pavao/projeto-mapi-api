package com.projeto.mapi.service;

import com.projeto.mapi.dto.TideTableResponseDTO;
import java.util.List;
import java.util.Optional;

public interface TideService {
    Optional<TideTableResponseDTO> getTideTable(String harborName, Integer year);
    List<TideTableResponseDTO> getTideTablesByState(String state, Integer year);
    List<TideTableResponseDTO> searchTideTablesByHarbor(String harborName, Integer year);
    List<String> getAllHarbors(Integer year);
}
