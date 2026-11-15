package com.projeto.mapi.service.analise;

import com.projeto.mapi.dto.PointRainCandidatesDTO;

import java.time.Instant;

/** Candidatas da chuva do ponto numa hora, com o papel de cada uma (ADR 0022). */
public interface PointRainCandidatesService {

    /** {@code at} = fim da hora; nulo = última hora com série. ResourceNotFoundException se o slug não existe. */
    PointRainCandidatesDTO candidates(String slug, Instant at);
}
