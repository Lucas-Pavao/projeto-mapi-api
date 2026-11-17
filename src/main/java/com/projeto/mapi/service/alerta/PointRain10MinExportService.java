package com.projeto.mapi.service.alerta;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;

/** Série de 10 min do ponto em CSV, pela mesma regra do alerta, para o histórico (itens 1.4.1 e 1.4.6). */
public interface PointRain10MinExportService {

    /** Escreve slot_end_utc, mm, station, n_valid, long_block, block_minutes por mês, sem carregar 5 anos na memória. */
    void writeCsv(String slug, Instant from, Instant to, Writer out) throws IOException;
}
