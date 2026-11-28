package com.projeto.mapi.service.alagamento;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projeto.mapi.dto.AlagamentoPontoDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de GET /api/alagamento/pontos para os 10 estados da prancha "Nível 6". As respostas da IA em
 * src/test/resources/alagamento/ia/ saíram do código real (projeto-mapi-ai/scripts/gerar_estados_alagamento.py).
 */
class FloodForecastContractTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final Instant NOW = Instant.parse("2026-11-20T17:00:00Z");

    private FloodForecastMapper.PointContext ctx() {
        return new FloodForecastMapper.PointContext(5L, "MASCARENHAS_IMBIRIBEIRA", "Av. Mascarenhas de Morais", "261160609A",
                "Imbiribeira", 1.1, 12.4, NOW, true);
    }

    private JsonNode ia(String estado) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/alagamento/ia/" + estado + ".json")) {
            return mapper.readTree(in);
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(nullValues = "-", value = {
            "sem_alerta_seco,             SEM_ALERTA, 6, -",
            "chuva_forte_sem_encher,      SEM_ALERTA, 6, -",
            "atencao_3h,                  ATENCAO,    3, -",
            "alerta_1h,                   ALERTA,     1, -",
            "alerta_agora,                ALERTA,     1, -",
            "atencao_6h,                  ATENCAO,    6, -",
            "sem_dado_chuva_ausente,      SEM_DADO,   -, CHUVA_AUSENTE",
            "sem_dado_previsao_fora,      SEM_DADO,   -, PREVISAO_INDISPONIVEL",
            "sem_dado_sem_terreno,        SEM_DADO,   -, SEM_TERRENO",
            "sem_dado_terreno_em_revisao, SEM_DADO,   -, TERRENO_EM_REVISAO",
            "alerta_mare_3h,              ALERTA,     3, -",
            "sem_dado_mare_ausente,       SEM_DADO,   -, MARE_AUSENTE",
            "atencao_faixa_capacidade,    ATENCAO,    1, -"})
    void dezEstados(String estado, String level, Integer worst, String reason) throws Exception {
        AlagamentoPontoDTO d = FloodForecastMapper.toDto(ia(estado), ctx());
        assertThat(d.level()).isEqualTo(level);
        assertThat(d.worstHorizonHours()).isEqualTo(worst);
        assertThat(d.reason()).isEqualTo(reason);
        assertThat(d.experimental()).isTrue();
        assertThat(d.attribution()).isEqualTo("DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC");
        assertThat(d.evaluatedAt()).isEqualTo(NOW);
        if ("SEM_DADO".equals(level)) {
            assertThat(d.reasonText()).isNotBlank();
            assertThat(d.horizons()).isEmpty();
            assertThat(d.explanation()).isNull();
        } else {
            assertThat(d.reasonText()).isNull();
            assertThat(d.horizons()).extracting(AlagamentoPontoDTO.Horizon::hours).containsExactly(1, 3, 6);
            assertThat(d.horizons()).allSatisfy(h -> {
                assertThat(h.validUntil()).isAfter(d.issuedAt());
                assertThat(h.sheetQ95Cm()).isGreaterThanOrEqualTo(h.sheetQ50Cm());
            });
            assertThat(d.explanation().shapTop3()).hasSize(3).allSatisfy(s -> assertThat(s.text()).contains("mm"));
            assertThat(d.explanation().decomposition()).isNotEmpty();
            assertThat(d.explanation().sensitivity()).isNotNull();
            assertThat(d.modelVersions()).containsEntry("rain", "rain_v1").containsEntry("bucket", "balde-M1a-10min")
                    .containsEntry("flood", "flood_reg_v2");
            assertThat(d.codeVersion()).hasSize(40);
            assertThat(d.sources()).contains("CEMADEN_PED");
            assertThat(d.hNowCm()).isNotNull();
        }
    }

    @Test
    void mareDoHorizonteEntraComoFatorComContribuicao() throws Exception {
        AlagamentoPontoDTO d = FloodForecastMapper.toDto(ia("alerta_mare_3h"), ctx());
        AlagamentoPontoDTO.Refluxo r = d.explanation().factors().refluxo();
        assertThat(r.status()).isEqualTo("OK");
        assertThat(r.appliedToLevel()).isTrue();
        assertThat(r.reductionNowPct()).isZero();
        assertThat(r.periods()).singleElement().satisfies(p -> {
            assertThat(p.from()).isEqualTo(Instant.parse("2026-11-20T18:00:00Z"));
            assertThat(p.meanReductionPct()).isEqualTo(100.0);
        });
        assertThat(r.servedDeltaCm()).isGreaterThan(15.0);
        assertThat(r.deltaSheetCm().get("q50")).containsEntry("1", 0.0);
        assertThat(r.biasScenario().mdtBiasM()).isEqualTo(-0.369);
        assertThat(r.biasScenario().level()).isEqualTo("SEM_ALERTA");   // só sensibilidade: o nível é o do central
        assertThat(r.text()).contains("15h00").contains("18h00");
        AlagamentoPontoDTO.Limpeza l = d.explanation().factors().limpeza();
        assertThat(l.mode()).isEqualTo("INFO");
        assertThat(l.appliedToLevel()).isFalse();
        assertThat(l.regime()).isEqualTo("PERMANENTE");
        assertThat(l.pClogged()).isBetween(0.5, 0.7);
        assertThat(l.text()).contains("não muda o nível");
        assertThat(d.explanation().decomposition()).filteredOn(s -> "previsto".equals(s.kind()))
                .anySatisfy(s -> assertThat(s.outflowFactor()).isZero()).allSatisfy(s -> assertThat(s.tideNrM()).isNotNull());
    }

    @Test
    void capacidadeEmFaixaComAsDuasPontasDaLamina() throws Exception {
        AlagamentoPontoDTO d = FloodForecastMapper.toDto(ia("atencao_faixa_capacidade"), ctx());
        AlagamentoPontoDTO.Capacity c = d.explanation().capacity();
        assertThat(c.qMinM3s()).isEqualTo(0.004);
        assertThat(c.qMaxM3s()).isEqualTo(0.5);
        assertThat(c.base()).contains("ESIG");
        assertThat(d.explanation().servedCapacity()).isEqualTo("q_min");
        AlagamentoPontoDTO.Horizon h1 = d.horizons().get(0);
        assertThat(h1.sheetQ50Cm()).isLessThan(15.0);                 // q50 com Q_max
        assertThat(h1.sheetQ95Cm()).isGreaterThanOrEqualTo(15.0);     // q95 com Q_min
        assertThat(h1.sheetQ50CmQmin()).isNotNull();
        assertThat(h1.sheetQ95CmQmax()).isNotNull();
        assertThat(d.explanation().profileMethod()).contains("por igual");
    }

    @Test
    void tornadoEBaseDoShapParaATela() throws Exception {
        AlagamentoPontoDTO.Explanation e = FloodForecastMapper.toDto(ia("alerta_1h"), ctx()).explanation();
        AlagamentoPontoDTO.Tornado t = e.tornado();
        assertThat(t.deltaPct()).isEqualTo(20);
        assertThat(t.bars()).hasSize(4);
        assertThat(t.bars().get(0).swingCm()).isGreaterThanOrEqualTo(t.bars().get(3).swingCm());
        assertThat(t.heaviest()).isEqualTo(t.bars().get(0).parameter());
        assertThat(t.text()).contains("±20%");
        JsonNode shap = ia("alerta_1h").path("explanation").path("shap");   // waterfall: outros = previsão - base - top 3
        assertThat(e.shapBaseMm()).isEqualTo(shap.path("base_mm").asDouble());
        assertThat(e.shapPredictionMm()).isEqualTo(shap.path("raw_prediction_mm").asDouble());
        assertThat(FloodForecastMapper.toDto(ia("sem_dado_chuva_ausente"), ctx()).explanation()).isNull();
    }

    @Test
    void respostaAntigaSemTornadoContinuaValendo() throws Exception {
        JsonNode antiga = ia("atencao_3h");
        ((com.fasterxml.jackson.databind.node.ObjectNode) antiga.path("explanation")).remove("tornado");
        assertThat(FloodForecastMapper.toDto(antiga, ctx()).explanation().tornado()).isNull();
    }

    @Test
    void mareAusenteComRefluxoNoNivelViraSemDadoComMotivo() throws Exception {
        AlagamentoPontoDTO d = FloodForecastMapper.toDto(ia("sem_dado_mare_ausente"), ctx());
        assertThat(d.reasonText()).contains("maré");
    }

    @Test
    void alertaJaAgoraTemLaminaDeAgoraAcimaDe15cm() throws Exception {
        AlagamentoPontoDTO d = FloodForecastMapper.toDto(ia("alerta_agora"), ctx());
        assertThat(d.hNowCm()).isGreaterThanOrEqualTo(15.0);
    }

    @Test
    void iaForaDoArViraSemDadoComMotivo_nuncaSemAlerta() {
        AlagamentoPontoDTO d = FloodForecastMapper.toDto(null, ctx());
        assertThat(d.level()).isEqualTo("SEM_DADO");
        assertThat(d.reason()).isEqualTo("PREVISAO_INDISPONIVEL");
        assertThat(d.reasonText()).isEqualTo("previsão de chuva (rain_v1) fora do ar");
    }

    @Test
    void nivelDesconhecidoOuSemMotivoViraSemDado() throws Exception {
        assertThat(FloodForecastMapper.toDto(mapper.readTree("{\"level\":\"BAIXO\"}"), ctx()).level()).isEqualTo("SEM_DADO");
        AlagamentoPontoDTO d = FloodForecastMapper.toDto(mapper.readTree("{\"level\":\"SEM_DADO\"}"), ctx());
        assertThat(d.reason()).isEqualTo("PREVISAO_INDISPONIVEL");
    }

    @Test
    void nomesDosCamposDoContrato() throws Exception {
        JsonNode j = mapper.readTree(mapper.writeValueAsString(FloodForecastMapper.toDto(ia("atencao_3h"), ctx())));
        assertThat(List.of("floodPointId", "slug", "name", "level", "worstHorizonHours", "reason", "reasonText", "hNowCm",
                "rainLastHourMm", "horizons", "explanation", "rainStation", "rainStationName", "rainStationDistanceKm", "latestReadingAgeMinutes",
                "modelVersions", "sources", "missingInputs", "codeVersion", "issuedAt", "evaluatedAt", "experimental", "shadow", "rule", "attribution"))
                .allSatisfy(k -> assertThat(j.has(k)).as(k).isTrue());
        assertThat(j.path("horizons").get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("hours", "level", "rainQ50Mm",
                "rainQ95Mm", "sheetQ50Cm", "sheetQ95Cm", "validUntil", "sheetQ50CmQmin", "sheetQ95CmQmax", "peak10Q50Mm",
                "peak10Q95Mm");
        assertThat(j.path("explanation").fieldNames()).toIterable().contains("shapTop3", "counterfactualMm", "sensitivity",
                "decomposition", "shapBaseMm", "shapPredictionMm", "tornado");
        assertThat(j.path("explanation").path("tornado").path("bars").get(0).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("parameter", "label", "minus20Cm", "plus20Cm", "swingCm");
        assertThat(j.path("explanation").path("sensitivity").fieldNames()).toIterable().contains("minCm", "maxCm", "levelChanges");
        assertThat(j.path("issuedAt").asText()).isEqualTo("2026-11-20T17:00:00Z");
        assertThat(j.path("rainStationName").asText()).isEqualTo("Imbiribeira");
    }
}
