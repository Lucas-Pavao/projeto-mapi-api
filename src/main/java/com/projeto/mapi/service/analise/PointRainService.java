package com.projeto.mapi.service.analise;

import com.projeto.mapi.dto.PointRainRebuildDTO;

import java.time.Instant;

/** Checagem espacial mensal e série horária de chuva por ponto, com o vínculo da chuva medida (ADR 0022). */
public interface PointRainService {

    String SOURCE = "CEMADEN_PED";
    String RULE_VERSION = "chuva-ponto-v3";
    String LINKAGE_METHOD = "MAX_VALID_MONTHLY";

    /** Recalcula as três tabelas derivadas para horas com fim em (from, to]; from e to em hora cheia UTC. */
    PointRainRebuildDTO rebuild(Instant from, Instant to);
}
