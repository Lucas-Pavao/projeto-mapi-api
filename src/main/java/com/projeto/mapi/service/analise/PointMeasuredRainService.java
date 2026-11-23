package com.projeto.mapi.service.analise;

import com.projeto.mapi.dto.PointMeasuredRainDTO;

import java.util.Optional;

/** Chuva medida acumulada do ponto na última hora fechada (onda 16, Fase 7.3; ADR 0022/0023). */
public interface PointMeasuredRainService {

    Optional<PointMeasuredRainDTO> latest(String slug);
}
