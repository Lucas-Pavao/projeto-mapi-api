package com.projeto.mapi.service.alerta.impl;

import com.projeto.mapi.config.AlertaProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Modo do refluxo por ponto (onda 16, Fase 3): Ibura em INFO, os outros com saída no modo geral (NIVEL). */
class RefluxoModoTest {

    @Test
    void iburaSoInformaOsOutrosSeguemOGeral() {
        AlertaProperties.Refluxo rf = new AlertaProperties.Refluxo();
        assertThat(AlertaCycleServiceImpl.refluxoModo(rf, rf.getPontos().get("AV_RECIFE_IBURA"))).isEqualTo("INFO");
        assertThat(AlertaCycleServiceImpl.refluxoModo(rf, rf.getPontos().get("MASCARENHAS_IMBIRIBEIRA"))).isEqualTo("NIVEL");
        assertThat(AlertaCycleServiceImpl.refluxoModo(rf, null)).isEqualTo("NIVEL");   // a IA devolve NAO_SE_APLICA
        rf.setEnabled(false);
        assertThat(AlertaCycleServiceImpl.refluxoModo(rf, rf.getPontos().get("MASCARENHAS_IMBIRIBEIRA"))).isEqualTo("OFF");
    }
}
