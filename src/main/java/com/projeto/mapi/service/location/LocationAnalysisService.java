package com.projeto.mapi.service.location;

import com.projeto.mapi.dto.LocationAnalysisDTO;

import java.time.Instant;

/** Análise de local do mapa (substitui o uso de /api/environmental-data no painel). */
public interface LocationAnalysisService {

    /** {@code at} nulo = agora. InvalidRequestException se lat/lon são inválidos. Cache de 10 min por célula de ~200 m. */
    LocationAnalysisDTO analyze(double lat, double lon, Instant at);

    /**
     * Só a chuva medida na coordenada exata, sem cache, sem clima e sem estimativa: estações canônicas a até 5 km e o
     * papel de cada uma na última hora fechada até {@code at}. Usado pelo card do pluviômetro (B6) quando a série do
     * ponto não cobre a hora.
     */
    LocationAnalysisDTO.Rain measuredRain(double lat, double lon, Instant at);
}
