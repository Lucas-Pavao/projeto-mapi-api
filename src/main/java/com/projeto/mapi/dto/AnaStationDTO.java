package com.projeto.mapi.dto;

/**
 * Estação telemétrica descoberta via ListaEstacoesTelemetricas (serviço legado público da ANA).
 */
public record AnaStationDTO(String code, String name, String river, Double latitude, Double longitude) {
}
