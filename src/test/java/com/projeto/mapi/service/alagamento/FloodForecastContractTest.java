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
        return new FloodForecastMapper.PointContext(5L, "MASCARENHAS_IMBIRIBEIRA", "Av. Mascarenhas de Morais", "260960001A",
                1.1, 12.4, NOW, true);
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
            "sem_dado_terreno_em_revisao, SEM_DADO,   -, TERRENO_EM_REVISAO"})
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
                    .containsEntry("flood", "flood_reg_v1");
            assertThat(d.codeVersion()).hasSize(40);
            assertThat(d.hNowCm()).isNotNull();
        }
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
                "rainLastHourMm", "horizons", "explanation", "rainStation", "rainStationDistanceKm", "latestReadingAgeMinutes",
                "modelVersions", "codeVersion", "issuedAt", "evaluatedAt", "experimental", "shadow", "rule", "attribution"))
                .allSatisfy(k -> assertThat(j.has(k)).as(k).isTrue());
        assertThat(j.path("horizons").get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("hours", "level", "rainQ50Mm",
                "rainQ95Mm", "sheetQ50Cm", "sheetQ95Cm", "validUntil");
        assertThat(j.path("explanation").fieldNames()).toIterable().contains("shapTop3", "counterfactualMm", "sensitivity",
                "decomposition");
        assertThat(j.path("explanation").path("sensitivity").fieldNames()).toIterable().contains("minCm", "maxCm", "levelChanges");
        assertThat(j.path("issuedAt").asText()).isEqualTo("2026-11-20T17:00:00Z");
    }
}
