package com.projeto.mapi.service.tide;

import com.projeto.mapi.dto.TideTableResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface TideService {
    Optional<TideTableResponseDTO> getTideTable(String harborName, Integer year);
    Page<TideTableResponseDTO> getTideTablesByState(String state, Integer year, Pageable pageable);
    Page<TideTableResponseDTO> searchTideTablesByHarbor(String harborName, Integer year, Pageable pageable);
    List<String> getAllHarbors(Integer year);
    Double getCurrentTideHeight(double latitude, double longitude);
    Double getTideHeightAt(double latitude, double longitude, java.time.LocalDateTime timestamp);
}
