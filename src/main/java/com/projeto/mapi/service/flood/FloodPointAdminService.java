package com.projeto.mapi.service.flood;

import com.projeto.mapi.dto.FloodPointPatchDTO;
import com.projeto.mapi.dto.FloodPointResponseDTO;
import com.projeto.mapi.dto.StationSuggestionDTO;

import java.util.List;

/** Edição de pontos críticos e apoio ao cadastro (ADMIN). */
public interface FloodPointAdminService {

    /** Aplica os campos informados (ao menos um) e devolve o mesmo corpo de GET /api/pontos/{slug}. */
    FloodPointResponseDTO patch(String slug, FloodPointPatchDTO patch);

    /** Estações a até {@code radiusM} da coordenada, ordenadas por distância. */
    List<StationSuggestionDTO> stationSuggestions(double lat, double lon, int radiusM);
}
