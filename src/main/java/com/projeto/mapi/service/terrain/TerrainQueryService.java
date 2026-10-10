package com.projeto.mapi.service.terrain;

import com.projeto.mapi.dto.TerrainResponseDTO;

public interface TerrainQueryService {

    /** Terreno do ponto na execução vigente. Lança ResourceNotFoundException se o ponto não existe. */
    TerrainResponseDTO findBySlug(String slug);
}
