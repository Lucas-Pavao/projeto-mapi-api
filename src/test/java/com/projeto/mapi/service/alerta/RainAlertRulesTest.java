package com.projeto.mapi.service.alerta;

import com.projeto.mapi.service.alerta.RainAlertRules.Bucket;
import com.projeto.mapi.service.alerta.RainAlertRules.Decision;
import com.projeto.mapi.service.alerta.RainAlertRules.Forecast;
import com.projeto.mapi.service.alerta.RainAlertRules.Measured;
import com.projeto.mapi.service.alerta.RainAlertRules.Params;
import com.projeto.mapi.service.alerta.RainAlertRules.Previous;
import com.projeto.mapi.service.alerta.RainAlertRules.TerrainStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RainAlertRulesTest {

    static final Instant NOW = Instant.parse("2026-11-17T15:00:00Z");
    static final double L_ON = 0.1055;

    static Params params() {
        return new Params(L_ON, Double.NaN, 0, 60, 30, 0.2, 60, 15, 5.0, 10.0, 150.0, 60);
    }

    static Measured fresh(double rain10, double rain30) {
        return new Measured(NOW.minusSeconds(300), rain10, rain30, rain10, false);
    }

    static Forecast prob(double p) {
        return new Forecast(p, 2.0, 5.0, L_ON, "rain_v1", null);
    }

    static Bucket dryBucket() {
        return new Bucket(0.0, null, null, 0.0, 0.0, "balde-M1a-10min", null);
    }

    static Decision decide(Measured m, Forecast f, Bucket b, TerrainStatus t, Previous prev) {
        return RainAlertRules.decide(NOW, m, f, b, t, prev, params());
    }

    @Test
    void capacidadeEmDezMinutosPelaIdfDeSilvaEAraujo() {
        assertThat(RainAlertRules.idfMmH(5, 10)).isCloseTo(125.40, org.assertj.core.data.Offset.offset(0.01));
        assertThat(RainAlertRules.capacityMm(5, 10)).isCloseTo(20.90, org.assertj.core.data.Offset.offset(0.01));
        assertThat(RainAlertRules.capacityMm(2, 15)).isCloseTo(23.43, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void semChuvaEProbabilidadeBaixaDaNenhumENao() {
        Decision d = decide(fresh(0, 0), prob(0.01), dryBucket(), TerrainStatus.OK, Previous.initial());
        assertThat(d.rain()).isEqualTo(RainAlertLevel.NENHUM);
        assertThat(d.flood()).isEqualTo(PointFloodLevel.NAO);
        assertThat(d.rainReason()).isNull();
        assertThat(d.transitions()).isEmpty();
    }

    @Test
    void probabilidadeAcimaDoLimiarLigaAtencaoComEvento() {
        Decision d = decide(fresh(3, 6), prob(0.2), dryBucket(), TerrainStatus.OK, Previous.initial());
        assertThat(d.rain()).isEqualTo(RainAlertLevel.ATENCAO);
        assertThat(d.eventId()).isNotNull();
        assertThat(d.transitions()).extracting(RainAlertRules.Transition::kind).containsExactly(TransitionKind.LIGA);
    }

    @Test
    void chuvaMedidaAcimaDaGaleriaDaAlertaMesmoComAIaFora() {
        Decision d = decide(fresh(21.0, 30), Forecast.failed(SemDadoMotivo.IA_INDISPONIVEL), dryBucket(), TerrainStatus.OK,
                Previous.initial());
        assertThat(d.rain()).isEqualTo(RainAlertLevel.ALERTA);
        assertThat(d.rainReason()).isNull();
    }

    @Test
    void atencaoSobeParaAlerta() {
        Previous prev = new Previous(RainAlertLevel.ATENCAO, PointFloodLevel.NAO, NOW.minusSeconds(600), NOW.minusSeconds(600),
                UUID.randomUUID(), NOW.minusSeconds(600));
        Decision d = decide(fresh(22, 40), prob(0.5), dryBucket(), TerrainStatus.OK, prev);
        assertThat(d.rain()).isEqualTo(RainAlertLevel.ALERTA);
        assertThat(d.transitions()).extracting(RainAlertRules.Transition::kind).containsExactly(TransitionKind.SOBE);
        assertThat(d.eventId()).isEqualTo(prev.eventId());
    }

    @Test
    void leituraVelhaViraSemDadoNuncaNenhum() {
        Measured old = new Measured(NOW.minusSeconds(20 * 60), 0.0, 0.0, 0.0, false);
        Decision d = decide(old, prob(0.01), dryBucket(), TerrainStatus.OK, Previous.initial());
        assertThat(d.rain()).isEqualTo(RainAlertLevel.SEM_DADO);
        assertThat(d.rainReason()).isEqualTo(SemDadoMotivo.LEITURA_VELHA);
        assertThat(d.flood()).isEqualTo(PointFloodLevel.SEM_DADO);
        assertThat(d.floodReason()).isEqualTo(SemDadoMotivo.LEITURA_VELHA);
    }

    @Test
    void semEstacaoValidaViraSemDado() {
        Decision d = decide(new Measured(null, null, null, null, false), prob(0.01), dryBucket(), TerrainStatus.OK,
                Previous.initial());
        assertThat(d.rainReason()).isEqualTo(SemDadoMotivo.SEM_ESTACAO_VALIDA);
        assertThat(d.floodReason()).isEqualTo(SemDadoMotivo.SEM_ESTACAO_VALIDA);
    }

    @Test
    void fonteParadaViraSemDado() {
        Measured m = new Measured(NOW.minusSeconds(300), 0.0, 0.0, 0.0, true);
        assertThat(decide(m, prob(0.01), dryBucket(), TerrainStatus.OK, Previous.initial()).rainReason())
                .isEqualTo(SemDadoMotivo.FONTE_PARADA);
    }

    @Test
    void iaIndisponivelERecusadaViramSemDadoComMotivoProprio() {
        assertThat(decide(fresh(0, 0), Forecast.failed(SemDadoMotivo.IA_INDISPONIVEL), dryBucket(), TerrainStatus.OK,
                Previous.initial()).rainReason()).isEqualTo(SemDadoMotivo.IA_INDISPONIVEL);
        Decision d = decide(fresh(0, 0), Forecast.failed(SemDadoMotivo.PREVISAO_RECUSADA), dryBucket(), TerrainStatus.OK,
                Previous.initial());
        assertThat(d.rainReason()).isEqualTo(SemDadoMotivo.PREVISAO_RECUSADA);
        assertThat(d.flood()).isEqualTo(PointFloodLevel.SEM_DADO);           // sem previsão não há projeção: nunca NAO
        assertThat(d.floodReason()).isEqualTo(SemDadoMotivo.SEM_PREVISAO);
    }

    @Test
    void semTerrenoETerrenoEmRevisaoViramSemDadoNoPontoMasOAlertaDeChuvaContinua() {
        Decision a = decide(fresh(3, 6), prob(0.3), Bucket.failed(SemDadoMotivo.SERIE_INCOMPLETA), TerrainStatus.SEM_TERRENO,
                Previous.initial());
        assertThat(a.flood()).isEqualTo(PointFloodLevel.SEM_DADO);
        assertThat(a.floodReason()).isEqualTo(SemDadoMotivo.SEM_TERRENO);
        assertThat(a.rain()).isEqualTo(RainAlertLevel.ATENCAO);
        Decision b = decide(fresh(0, 0), prob(0.01), Bucket.failed(SemDadoMotivo.SERIE_INCOMPLETA), TerrainStatus.EM_REVISAO,
                Previous.initial());
        assertThat(b.floodReason()).isEqualTo(SemDadoMotivo.TERRENO_EM_REVISAO);
        assertThat(b.rain()).isEqualTo(RainAlertLevel.NENHUM);
    }

    @Test
    void baldeIndisponivelESerieIncompletaViramSemDado() {
        assertThat(decide(fresh(0, 0), prob(0.01), Bucket.failed(SemDadoMotivo.BALDE_INDISPONIVEL), TerrainStatus.OK,
                Previous.initial()).floodReason()).isEqualTo(SemDadoMotivo.BALDE_INDISPONIVEL);
        assertThat(decide(fresh(0, 0), prob(0.01), Bucket.failed(SemDadoMotivo.SERIE_INCOMPLETA), TerrainStatus.OK,
                Previous.initial()).floodReason()).isEqualTo(SemDadoMotivo.SERIE_INCOMPLETA);
    }

    @Test
    void laminaAgoraOuProjetadaEmAte60MinDaProvavel() {
        Bucket now = new Bucket(160.0, 0, 0, 200.0, 250.0, "balde-M1a-10min", null);
        Decision a = decide(fresh(20, 50), prob(0.5), now, TerrainStatus.OK, Previous.initial());
        assertThat(a.flood()).isEqualTo(PointFloodLevel.PROVAVEL);
        assertThat(a.floodInMinutes()).isZero();
        Bucket soon = new Bucket(40.0, 30, 20, 180.0, 260.0, "balde-M1a-10min", null);
        Decision b = decide(fresh(15, 40), prob(0.5), soon, TerrainStatus.OK, Previous.initial());
        assertThat(b.flood()).isEqualTo(PointFloodLevel.PROVAVEL);
        assertThat(b.floodInMinutes()).isEqualTo(30);
        Bucket onlyQ80 = new Bucket(40.0, null, 50, 120.0, 170.0, "balde-M1a-10min", null);
        assertThat(decide(fresh(15, 40), prob(0.5), onlyQ80, TerrainStatus.OK, Previous.initial()).flood())
                .isEqualTo(PointFloodLevel.NAO);                               // q80 é faixa, não critério
    }

    @Test
    void histereseMantemAtencaoEntreLDesligaELLiga() {
        Params p = new Params(L_ON, 0.05, 0, 60, 30, 0.2, 60, 15, 5.0, 10.0, 150.0, 60);
        Previous prev = new Previous(RainAlertLevel.ATENCAO, PointFloodLevel.NAO, NOW.minusSeconds(900), NOW.minusSeconds(300),
                UUID.randomUUID(), NOW.minusSeconds(300));
        Decision keep = RainAlertRules.decide(NOW, fresh(1, 3), prob(0.07), dryBucket(), TerrainStatus.OK, prev, p);
        assertThat(keep.rain()).isEqualTo(RainAlertLevel.ATENCAO);
        assertThat(keep.transitions()).isEmpty();
        Decision off = RainAlertRules.decide(NOW, fresh(1, 3), prob(0.03), dryBucket(), TerrainStatus.OK, prev, p);
        assertThat(off.rain()).isEqualTo(RainAlertLevel.NENHUM);
        assertThat(off.transitions()).extracting(RainAlertRules.Transition::kind).containsExactly(TransitionKind.DESLIGA);
    }

    @Test
    void semRenovacaoPor60MinExpira() {
        Params p = new Params(L_ON, 0.05, 0, 60, 30, 0.2, 60, 15, 5.0, 10.0, 150.0, 60);
        Previous prev = new Previous(RainAlertLevel.ATENCAO, PointFloodLevel.NAO, NOW.minusSeconds(7200), NOW.minusSeconds(3600),
                UUID.randomUUID(), NOW.minusSeconds(300));
        Decision d = RainAlertRules.decide(NOW, fresh(1, 3), prob(0.07), dryBucket(), TerrainStatus.OK, prev, p);
        assertThat(d.rain()).isEqualTo(RainAlertLevel.NENHUM);
        assertThat(d.transitions()).extracting(RainAlertRules.Transition::kind).containsExactly(TransitionKind.EXPIRA);
    }

    @Test
    void tempoSecoCancelaMesmoDentroDaDuracaoMinima() {
        Params p = new Params(L_ON, 0.05, 30, 60, 30, 0.2, 60, 15, 5.0, 10.0, 150.0, 60);
        Previous prev = new Previous(RainAlertLevel.ATENCAO, PointFloodLevel.NAO, NOW.minusSeconds(600), NOW.minusSeconds(600),
                UUID.randomUUID(), NOW.minusSeconds(600));
        Decision cancel = RainAlertRules.decide(NOW, fresh(0, 0.1), prob(0.01), dryBucket(), TerrainStatus.OK, prev, p);
        assertThat(cancel.rain()).isEqualTo(RainAlertLevel.NENHUM);
        assertThat(cancel.transitions()).extracting(RainAlertRules.Transition::kind).containsExactly(TransitionKind.CANCELA);
        Decision held = RainAlertRules.decide(NOW, fresh(1, 2), prob(0.01), dryBucket(), TerrainStatus.OK, prev, p);
        assertThat(held.rain()).isEqualTo(RainAlertLevel.ATENCAO);           // duração mínima segura o DESLIGA com chuva
    }

    @Test
    void novoDisparoMenosDe60MinDepoisDoUltimoAtivoUsaOMesmoEvento() {
        UUID ev = UUID.randomUUID();
        Previous recent = new Previous(RainAlertLevel.NENHUM, PointFloodLevel.NAO, NOW.minusSeconds(1200), null, ev,
                NOW.minusSeconds(30 * 60));
        assertThat(decide(fresh(3, 6), prob(0.3), dryBucket(), TerrainStatus.OK, recent).eventId()).isEqualTo(ev);
        Previous old = new Previous(RainAlertLevel.NENHUM, PointFloodLevel.NAO, NOW.minusSeconds(7200), null, ev,
                NOW.minusSeconds(90 * 60));
        assertThat(decide(fresh(3, 6), prob(0.3), dryBucket(), TerrainStatus.OK, old).eventId()).isNotEqualTo(ev);
    }

    @ParameterizedTest
    @EnumSource(SemDadoMotivo.class)
    void todoMotivoTemTexto(SemDadoMotivo m) {
        assertThat(m.texto()).isNotBlank();
    }

    @Test
    void nenhumDadoFaltandoProduzNenhumOuNao() {
        // varre combinações de falha: com qualquer falha de entrada, nenhum dos campos sai NENHUM/NAO
        Measured[] ms = {new Measured(null, null, null, null, false), new Measured(NOW.minusSeconds(3600), 0.0, 0.0, 0.0, false),
                new Measured(NOW.minusSeconds(60), 0.0, 0.0, 0.0, true)};
        Forecast[] fs = {Forecast.failed(SemDadoMotivo.IA_INDISPONIVEL), Forecast.failed(SemDadoMotivo.PREVISAO_RECUSADA)};
        for (Measured m : ms) {
            Decision d = decide(m, prob(0.01), dryBucket(), TerrainStatus.OK, Previous.initial());
            assertThat(d.rain()).isEqualTo(RainAlertLevel.SEM_DADO);
            assertThat(d.flood()).isEqualTo(PointFloodLevel.SEM_DADO);
        }
        for (Forecast f : fs) {
            Decision d = decide(fresh(0, 0), f, dryBucket(), TerrainStatus.OK, Previous.initial());
            assertThat(d.rain()).isEqualTo(RainAlertLevel.SEM_DADO);
            assertThat(d.flood()).isEqualTo(PointFloodLevel.SEM_DADO);
        }
    }
}
