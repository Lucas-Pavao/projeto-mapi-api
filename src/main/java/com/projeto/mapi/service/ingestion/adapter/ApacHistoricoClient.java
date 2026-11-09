package com.projeto.mapi.service.ingestion.adapter;

import java.time.LocalDate;

/**
 * Acesso ao histórico pluviométrico da APAC: consulta em {@code diario.php} e exportação XLSX em
 * {@code export_diario.php}, com os mesmos filtros. Interface para ser substituída nos testes.
 */
public interface ApacHistoricoClient {

    /** URL da exportação, filtros enviados (texto, para a proveniência) e o XLSX bruto. */
    record Response(String url, String filters, byte[] body) {}

    /** Total diário dos postos da mesorregião configurada em {@code [first, last]} (datas locais inclusivas). */
    Response daily(LocalDate first, LocalDate last);
}
