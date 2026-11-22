package com.projeto.mapi.service.limpeza;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class Central156Test {

    static final String CSV = "﻿\"GRUPOSERVICO_CODIGO\";\"GRUPOSERVICO_DESCRICAO\";\"SERVICO_CODIGO\";\"SERVICO_DESCRICAO\";\"LOGRADOURO\";\"NUMERO\";"
            + "\"BAIRRO\";\"RPA\";\"DATA_DEMANDA\";\"SITUACAO\";\"DATA_ULT_SITUACAO\";\"latitude\";\"longitude\"\n"
            + "\"11\";\"DRENAGEM   \";\"4\";\"LIMP. DE GALERIAS,VALETAS,SARJETAS  \";\"av  Recife   \";\"   \";\"IPSEP  \";\"6\";\"2021-03-01\";\"ATENDIDA    \";\"2021-03-20\";\"-8.10800\";\"-34.92710\"\n"
            + "\"27\";\"DRENAGEM\";\"8\";\"LIMPEZA DE GALERIAS E VALETAS\";\"AV   Marechal Mascarenhas de Moraes\";\"2557\";\"Imbiribeira\";\"6\";\"2025-05-02\";\"CADASTRADA\";\"2025-05-02\";\"\";\"\"\n"
            + "\"2\";\"LIMPEZA URBANA\";\"80\";\"CAPINACAO\";\"RUA X\";\"1\";\"Curado\";\"5\";\"2025-01-23\";\"PREPARACAO\";\"2025-01-23\";\"-8.0\";\"-34.9\"\n";

    static final List<Central156.PontoRef> PONTOS = List.of(
            new Central156.PontoRef(1, "AV_RECIFE_IBURA", -8.107984, -34.927161, "RECIFE", "IPSEP"),
            new Central156.PontoRef(2, "MASCARENHAS_IMBIRIBEIRA", -8.107178, -34.911237, "MARECHAL MASCARENHAS DE MORAES", "IMBIRIBEIRA"));

    @Test
    void le_o_csv_anual_e_normaliza() throws Exception {
        List<Central156.Chamado> cs = Central156.parse(CSV, 2021);
        assertThat(cs).hasSize(3);
        Central156.Chamado c = cs.get(0);
        assertThat(c.groupCode()).isEqualTo(11);
        assertThat(c.serviceCode()).isEqualTo(4);
        assertThat(c.logradouro()).isEqualTo("RECIFE");
        assertThat(c.bairro()).isEqualTo("IPSEP");
        assertThat(c.atendida()).isTrue();
        assertThat(c.lastStatusDate()).isEqualTo(LocalDate.of(2021, 3, 20));
        assertThat(c.rowSha256()).hasSize(64);
        assertThat(cs.get(1).lat()).isNull();
    }

    @Test
    void casa_por_coordenada_e_por_logradouro_e_bairro() throws Exception {
        List<Central156.Chamado> cs = Central156.parse(CSV, 2021);
        var m0 = Central156.casar(cs.get(0), PONTOS, 150.0);
        assertThat(m0).singleElement().satisfies(m -> {
            assertThat(m.ponto().slug()).isEqualTo("AV_RECIFE_IBURA");
            assertThat(m.regra()).isEqualTo("COORDENADA_150M");
        });
        var m1 = Central156.casar(cs.get(1), PONTOS, 150.0);
        assertThat(m1).singleElement().satisfies(m -> assertThat(m.regra()).isEqualTo("LOGRADOURO_BAIRRO"));
        assertThat(Central156.casar(cs.get(2), PONTOS, 150.0)).isEmpty();
    }

    @Test
    void normaliza_como_o_script_de_analise() {
        assertThat(Central156.normLogradouro("AV   Gov. Agamenon Magalhães")).isEqualTo("GOV AGAMENON MAGALHAES");
        assertThat(Central156.normLogradouro("1TR  Rua Nova")).isEqualTo("NOVA");
        assertThat(Central156.norm("Boa Viagem ")).isEqualTo("BOA VIAGEM");
    }

    @Test
    void probabilidade_de_entupida_pelo_tempo_desde_a_limpeza() {
        assertThat(Central156.pEntupida(30.0, 0.02337)).hasValueSatisfying(p -> assertThat(p).isCloseTo(0.5040, within(1e-3)));
        assertThat(Central156.pEntupida(0.0, 0.02337)).contains(0.0);
        assertThat(Central156.pEntupida(null, 0.02)).isEmpty();
        assertThat(Central156.pEntupida(10.0, null)).isEmpty();
    }
}
