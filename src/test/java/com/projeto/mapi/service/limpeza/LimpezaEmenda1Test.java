package com.projeto.mapi.service.limpeza;

import com.projeto.mapi.config.LimpezaProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Emenda 1 do pré-registro da 156 (2026-11-22): E1 coordenadas-padrão, E3 limpeza conhecida, E4 regime permanente. */
class LimpezaEmenda1Test {

    private static LimpezaProperties.Ponto ponto(Integer p95, Double de) {
        LimpezaProperties.Ponto p = new LimpezaProperties.Ponto();
        p.setLambdaRePorDia(0.0333);
        p.setLimpezas(130);
        p.setIntervaloP95Dias(p95);
        p.setDiasEntupidaMedia(de);
        return p;
    }

    private static final LocalDate FIM = LocalDate.of(2026, 6, 19);

    @Test
    void limpezaRecenteDentroDaCoberturaUsaOTempoDesdeALimpeza() {
        var d = LimpezaService.decidir(ponto(221, 44.2), LocalDate.of(2026, 5, 1), FIM, LocalDate.of(2026, 7, 1));
        assertThat(d.regime()).isEqualTo("TEMPO_DESDE_LIMPEZA");
        assertThat(d.dias()).isEqualTo(61.0);
    }

    @Test
    void limpezaVelhaDemaisParaACoberturaViraPermanente() {
        // Mascarenhas: última em 2023-08-31, 1.023 dias antes do fim, p95 = 221
        var d = LimpezaService.decidir(ponto(221, 44.2), LocalDate.of(2023, 8, 31), FIM, LocalDate.of(2026, 11, 22));
        assertThat(d.regime()).isEqualTo("PERMANENTE");
        assertThat(d.dias()).isNull();
        assertThat(d.diasEntupidaMedia()).isEqualTo(44.2);
        assertThat(d.motivo()).contains("mais antiga que 221 dias");
    }

    @Test
    void baseQueAcabouHaMaisDeNpDiasTambemViraPermanente() {
        var d = LimpezaService.decidir(ponto(221, 44.2), LocalDate.of(2026, 6, 1), FIM, LocalDate.of(2027, 3, 1));
        assertThat(d.regime()).isEqualTo("PERMANENTE");
        assertThat(d.motivo()).contains("a base da 156 acabou");
    }

    @Test
    void semNenhumaLimpezaEPermanente_semTempoMedioNaoHaNumero() {
        assertThat(LimpezaService.decidir(ponto(221, 44.2), null, FIM, FIM).regime()).isEqualTo("PERMANENTE");
        assertThat(LimpezaService.decidir(ponto(221, null), null, FIM, FIM)).isNull();
    }

    @Test
    void coordenadaPadraoNaoCasaPorCoordenadaMasCasaPorLogradouroEBairro() {
        var ref = new Central156.PontoRef(1, "P", -8.05, -34.95, "RECIFE", "IBURA");
        var perto = new Central156.Chamado("x", 2025, 2, 4, "s", "RECIFE", "IPSEP", LocalDate.of(2025, 1, 1), null, "ATENDIDA",
                -8.0501, -34.9501);
        Set<String> padrao = Set.of(Central156.chaveCoordenada(-8.0501, -34.9501));
        assertThat(Central156.casar(perto, List.of(ref), 150)).extracting(Central156.Casamento::regra).containsExactly("COORDENADA_150M");
        assertThat(Central156.casar(perto, List.of(ref), 150, padrao)).isEmpty();
        var mesmaRua = new Central156.Chamado("y", 2025, 2, 4, "s", "RECIFE", "IBURA", LocalDate.of(2025, 1, 1), null, "ATENDIDA",
                -8.0501, -34.9501);
        assertThat(Central156.casar(mesmaRua, List.of(ref), 150, padrao)).extracting(Central156.Casamento::regra)
                .containsExactly("LOGRADOURO_BAIRRO");
    }

    @Test
    void recursoDasCoordenadasPadraoCarrega() {
        assertThat(LimpezaService.carregarCoordenadasPadrao()).hasSize(109);
    }
}
