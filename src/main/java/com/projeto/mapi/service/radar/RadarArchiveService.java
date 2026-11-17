package com.projeto.mapi.service.radar;

/** Grava as imagens do radar da APAC em volume próprio, com manifesto no banco (V33). */
public interface RadarArchiveService {

    /** Resultado de uma rodada: arquivos novos, ausentes registrados e falhas. */
    record Result(int saved, int absent, int errors, boolean sourceUnavailable) {}

    Result collect();
}
