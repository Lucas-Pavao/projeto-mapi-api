package com.projeto.mapi.service.flood;

/**
 * Entradas físicas do modelo v3 na hora de referência da chuva (etapa A3). Nulo = ausente, nunca zero.
 * {@code soilMoisture} é reanálise (ERA5-Land) e chega com semanas de atraso: ao vivo costuma vir ausente.
 */
public record PointPhysicalInputs(Double rainMax10minMm, Double tideTrendMH, Double riverLevelM, Double soilMoisture) {
}
