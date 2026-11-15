package com.projeto.mapi.service.ingestion.adapter.ped;

import java.time.Instant;

/** Webservices da PED usados na carga (ADR 0021). Falha vira {@link com.projeto.mapi.exception.SourceUnavailableException}. */
public interface CemadenPedClient {

    /**
     * Resposta de um webservice. {@code noData} = HTTP 202 "Nenhum resultado foi encontrado": não há dado no
     * intervalo; não é erro nem zero de chuva.
     */
    record Response(int httpStatus, String url, String body, boolean noData) {}

    /** {@code /pcds-cadastro/dados-cadastrais?codibge=...}: estações do município com coordenada e instalação. */
    Response registry(String ibgeCode);

    /** {@code /pcds/dados_pcd?rede=11&codigo=...&sensor=10}: chuva de uma estação em [from, to] (até 30 dias). */
    Response rain(String stationCode, Instant from, Instant to);

    /**
     * {@code /pcds-acum/acumulados-recentes?codibge=...}: acumulados de pluviômetro de 1 a 120 h por estação do
     * município, calculados pela PED (ADR 0023). {@code null} numa janela = sem leitura; {@code datahora} é o instante
     * do cálculo, em UTC sem sufixo (conferido em 2026-11-15 contra o relógio UTC da máquina).
     */
    Response recentAccumulations(String ibgeCode);
}
