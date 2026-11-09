package com.projeto.mapi.service.ingestion;

import java.time.Duration;

/**
 * Valor extraído de um registro bruto, ainda sem fuso nem validação. {@code unit} nulo = canônica.
 *
 * @param window duração do bloco que termina no carimbo, quando a fonte a conhece (chuva por bloco do Cemaden PED,
 *               ADR 0021); nula nas demais fontes. Bloco mais longo que o máximo aceito vai para a quarentena.
 */
public record DraftValue(String variable, String resolution, Double value, String unit, String qualifier,
                         Duration window) {

    public DraftValue(String variable, String resolution, Double value, String unit, String qualifier) {
        this(variable, resolution, value, unit, qualifier, null);
    }
}
