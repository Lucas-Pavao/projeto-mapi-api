package com.projeto.mapi.service.ingestion.adapter;

import java.time.LocalDate;

/**
 * Acesso HTTP ao serviço legado público da ANA ({@code ServiceANA.asmx}, sem credencial). Interface para ser
 * substituída nos testes. As duas operações devolvem o XML bruto, que é o que o pipeline precisa guardar.
 */
public interface AnaLegacyClient {

    /** URL efetiva e corpo bruto (XML) da resposta. */
    record Response(String url, String body) {}

    /** {@code ListaEstacoesTelemetricas}: inventário completo (todas as UFs). */
    Response inventory();

    /** {@code DadosHidrometeorologicos}: série de 15 min de uma estação em {@code [start, end]} (datas locais inclusivas). */
    Response series(String stationCode, LocalDate start, LocalDate end);
}
