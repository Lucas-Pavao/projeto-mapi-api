package com.projeto.mapi.service.location;

import com.projeto.mapi.dto.LocationAnalysisDTO;

import java.time.Instant;

/** Análise de local do mapa (substitui o uso de /api/environmental-data no painel). */
public interface LocationAnalysisService {

    /** {@code at} nulo = agora. InvalidRequestException se lat/lon são inválidos. Cache de 10 min por célula de ~200 m. */
    LocationAnalysisDTO analyze(double lat, double lon, Instant at);
}
