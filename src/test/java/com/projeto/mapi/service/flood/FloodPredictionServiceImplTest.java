package com.projeto.mapi.service.flood;

import com.projeto.mapi.dto.DegradationReason;
import com.projeto.mapi.dto.FloodPredictionRequestDTO;
import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.dto.ModelValidationStatus;
import com.projeto.mapi.dto.RiskLevel;
import com.projeto.mapi.service.flood.impl.FloodPredictionServiceImpl;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** ADR 0007 §2-§4: coerência do payload da IA e mapeamento de falha, com a IA simulada. */
class FloodPredictionServiceImplTest {

    private static final String SEGREDO = "stacktrace-interno-nao-vaza";
    private static final String URL = "http://ai.local/v1/predict/flood";

    private MockRestServiceServer server;
    private FloodPredictionServiceImpl service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.local");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new FloodPredictionServiceImpl(builder.build(), new FeatureCatalog());
    }

    private FloodPredictionRequestDTO request() {
        return FloodPredictionRequestDTO.builder().stationId("S1").latitude(-8.05).longitude(-34.9).build();
    }

    private void iaResponde(String json) {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private FloodPredictionResponseDTO predizEConfere() {
        FloodPredictionResponseDTO r = service.predict(request(), List.of()).response();
        assertThat(PredictionCoherence.violation(r)).as("o backend nunca emite payload incoerente").isEmpty();
        assertThat(r.estimatedTimeToEvent()).isNull();
        return r;
    }

    private void assertUnknown(FloodPredictionResponseDTO r, DegradationReason reason) {
        assertThat(r.riskLevel()).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(r.floodProbability()).isNull();
        assertThat(r.degradationReason()).isEqualTo(reason);
        assertThat(r.message()).isEqualTo(reason.message()).doesNotContain(SEGREDO);
        assertThat(r.issuedAt()).isNotNull();
    }

    // ---- sucesso ----

    @Test
    void sucessoCompletoTraduzOContrato() {
        iaResponde("""
                {"flood_probability":0.42,"risk_level":"MEDIUM","model_version":"xgb-h3-2026.11.0+a1b2c3d",
                 "horizon_hours":3,"issued_at":"2026-10-10T12:00:00Z",
                 "top_contributors":[{"feature_id":"tide_height_t1","contribution":0.18},
                                     {"feature_id":"fator_novo","contribution":0.05}],
                 "imputed_features":["river_level"],"station_id":"S1","campo_extra":1}""");

        PredictionResult result = service.predict(request(), List.of("tide_level"));
        FloodPredictionResponseDTO r = result.response();

        assertThat(PredictionCoherence.violation(r)).isEmpty();
        assertThat(r.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(r.floodProbability()).isEqualTo(0.42);
        assertThat(r.modelVersion()).isEqualTo("xgb-h3-2026.11.0");
        assertThat(result.auditModelVersion()).isEqualTo("xgb-h3-2026.11.0+a1b2c3d");
        assertThat(r.issuedAt()).isEqualTo(Instant.parse("2026-10-10T12:00:00Z"));
        assertThat(r.horizonHours()).isEqualTo(3);
        assertThat(r.validUntil()).isEqualTo(Instant.parse("2026-10-10T15:00:00Z"));
        assertThat(r.lastValidIssuedAt()).isEqualTo(r.issuedAt());
        assertThat(r.degradationReason()).isNull();
        assertThat(r.contributors()).extracting("label")
                .containsExactly("Maré prevista na próxima hora", "Outro fator");
        assertThat(r.imputedFeatures()).extracting("featureId").containsExactly("tide_level", "river_level");
        assertThat(r.imputedFeatures()).extracting("label").containsExactly("Nível da maré", "Nível do rio");
    }

    @Test
    void camposOpcionaisAusentesSaoAceitos() {
        iaResponde("{\"flood_probability\":0.1,\"risk_level\":\"LOW\",\"model_version\":\"unversioned\"}");
        FloodPredictionResponseDTO r = predizEConfere();
        assertThat(r.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(r.modelVersion()).isEqualTo("unversioned");
        assertThat(r.horizonHours()).isNull();
        assertThat(r.validUntil()).isNull();
        assertThat(r.contributors()).isEmpty();
        assertThat(r.imputedFeatures()).isEmpty();
    }

    @Test
    void riscoDaIaEmMinusculasEAceito() {
        iaResponde("{\"flood_probability\":0.9,\"risk_level\":\"high\",\"model_version\":\"v1\"}");
        assertThat(predizEConfere().riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    // ---- ADR 0007 Emenda 1: modelValidationStatus ----

    @Test
    void validationStatusAusenteViraNotValidated() {
        iaResponde("{\"flood_probability\":0.1,\"risk_level\":\"LOW\",\"model_version\":\"unversioned\"}");
        assertThat(predizEConfere().modelValidationStatus()).isEqualTo(ModelValidationStatus.NOT_VALIDATED);
    }

    @Test
    void validationStatusNotValidatedDaIaEPropagado() {
        iaResponde("{\"flood_probability\":0.1,\"risk_level\":\"LOW\",\"model_version\":\"unversioned\","
                + "\"model_validation_status\":\"NOT_VALIDATED\"}");
        assertThat(predizEConfere().modelValidationStatus()).isEqualTo(ModelValidationStatus.NOT_VALIDATED);
    }

    @Test
    void validatedDaIaERebaixadoSemAdr() {
        iaResponde("{\"flood_probability\":0.1,\"risk_level\":\"LOW\",\"model_version\":\"v1\","
                + "\"model_validation_status\":\"VALIDATED\"}");
        assertThat(predizEConfere().modelValidationStatus()).isEqualTo(ModelValidationStatus.NOT_VALIDATED);
    }

    @Test
    void validationStatusDesconhecidoViraNotValidated() {
        iaResponde("{\"flood_probability\":0.1,\"risk_level\":\"LOW\",\"model_version\":\"v1\","
                + "\"model_validation_status\":\"???\"}");
        assertThat(predizEConfere().modelValidationStatus()).isEqualTo(ModelValidationStatus.NOT_VALIDATED);
    }

    @Test
    void unknownTambemTemNotValidated() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON).body("{}"));
        assertThat(predizEConfere().modelValidationStatus()).isEqualTo(ModelValidationStatus.NOT_VALIDATED);
    }

    // ---- §4: mapeamento de falha ----

    @Test
    void http422ViraInsufficientData() {
        server.expect(requestTo(URL)).andRespond(
                withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"" + SEGREDO + "\",\"missing_features\":[\"river_level\"]}"));
        assertUnknown(predizEConfere(), DegradationReason.INSUFFICIENT_DATA);
    }

    @Test
    void http422DeValidacaoDoEsquemaViraAiError() {
        server.expect(requestTo(URL)).andRespond(
                withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":[{\"type\":\"string_type\",\"loc\":[\"body\",\"nearby_sensors\",0,\"unit\"],\"msg\":\"" + SEGREDO + "\"}]}"));
        assertUnknown(predizEConfere(), DegradationReason.AI_ERROR);
    }

    @Test
    void timeoutDeLeituraViraAiTimeout() {
        server.expect(requestTo(URL)).andRespond(withException(new HttpTimeoutException(SEGREDO)));
        assertUnknown(predizEConfere(), DegradationReason.AI_TIMEOUT);
    }

    @Test
    void socketTimeoutDeLeituraViraAiTimeout() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out " + SEGREDO)));
        assertUnknown(predizEConfere(), DegradationReason.AI_TIMEOUT);
    }

    @Test
    void conexaoRecusadaViraAiUnavailable() {
        server.expect(requestTo(URL)).andRespond(withException(new ConnectException("Connection refused " + SEGREDO)));
        assertUnknown(predizEConfere(), DegradationReason.AI_UNAVAILABLE);
    }

    @Test
    void timeoutDeConexaoViraAiUnavailable() {
        server.expect(requestTo(URL)).andRespond(withException(new HttpConnectTimeoutException(SEGREDO)));
        assertUnknown(predizEConfere(), DegradationReason.AI_UNAVAILABLE);
    }

    @Test
    void circuitBreakerAbertoViraAiUnavailable() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("ai-teste");
        cb.transitionToOpenState();
        CallNotPermittedException aberto = CallNotPermittedException.createCallNotPermittedException(cb);
        server.expect(requestTo(URL)).andRespond(request -> {
            throw aberto;
        });
        assertUnknown(predizEConfere(), DegradationReason.AI_UNAVAILABLE);
    }

    @Test
    void http5xxViraAiError() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(SEGREDO));
        assertUnknown(predizEConfere(), DegradationReason.AI_ERROR);
    }

    @Test
    void http503ModeloNaoCarregadoViraAiError() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON).body("{\"detail\":\"Model not loaded\"}"));
        assertUnknown(predizEConfere(), DegradationReason.AI_ERROR);
    }

    @Test
    void corpoInvalidoViraAiError() {
        iaResponde("isto nao e json " + SEGREDO);
        assertUnknown(predizEConfere(), DegradationReason.AI_ERROR);
    }

    @Test
    void corpoVazioViraAiError() {
        server.expect(requestTo(URL)).andRespond(withSuccess());
        assertUnknown(predizEConfere(), DegradationReason.AI_ERROR);
    }

    @Test
    void imputacaoContinuaDeclaradaMesmoEmFalha() {
        server.expect(requestTo(URL)).andRespond(withException(new ConnectException("x")));
        FloodPredictionResponseDTO r = service.predict(request(), List.of("river_level")).response();
        assertThat(r.imputedFeatures()).extracting("featureId").containsExactly("river_level");
    }

    // ---- §2: payload incoerente da IA vira UNKNOWN/AI_ERROR ----

    @Test
    void riscoUnknownVindoDaIaEIncoerente() {
        iaResponde("{\"flood_probability\":0.5,\"risk_level\":\"UNKNOWN\",\"model_version\":\"v1\"}");
        assertUnknown(predizEConfere(), DegradationReason.AI_ERROR);
    }

    @Test
    void riscoForaDoEnumEIncoerente() {
        for (String risco : new String[]{"CRITICAL", "EXTREME", "", "null"}) {
            setUp();
            iaResponde("{\"flood_probability\":0.5,\"risk_level\":\"" + risco + "\",\"model_version\":\"v1\"}");
            assertUnknown(predizEConfere(), DegradationReason.AI_ERROR);
        }
    }

    @Test
    void riscoAusenteEIncoerente() {
        iaResponde("{\"flood_probability\":0.5,\"model_version\":\"v1\"}");
        assertUnknown(predizEConfere(), DegradationReason.AI_ERROR);
    }

    @Test
    void probabilidadeForaDaFaixaOuAusenteEIncoerente() {
        for (String prob : new String[]{"1.5", "-0.2", "null"}) {
            setUp();
            iaResponde("{\"flood_probability\":" + prob + ",\"risk_level\":\"HIGH\",\"model_version\":\"v1\"}");
            assertUnknown(predizEConfere(), DegradationReason.AI_ERROR);
        }
    }

    @Test
    void modeloSemVersaoEIncoerente() {
        iaResponde("{\"flood_probability\":0.5,\"risk_level\":\"LOW\"}");
        assertUnknown(predizEConfere(), DegradationReason.AI_ERROR);
        setUp();
        iaResponde("{\"flood_probability\":0.5,\"risk_level\":\"LOW\",\"model_version\":\"  \"}");
        assertUnknown(predizEConfere(), DegradationReason.AI_ERROR);
    }

    @Test
    void horizonteInvalidoDataIlegivelOuContribuinteInvalidoSaoIncoerentes() {
        iaResponde("{\"flood_probability\":0.5,\"risk_level\":\"LOW\",\"model_version\":\"v\",\"horizon_hours\":0}");
        assertUnknown(predizEConfere(), DegradationReason.AI_ERROR);
        setUp();
        iaResponde("{\"flood_probability\":0.5,\"risk_level\":\"LOW\",\"model_version\":\"v\",\"issued_at\":\"ontem\"}");
        assertUnknown(predizEConfere(), DegradationReason.AI_ERROR);
        setUp();
        iaResponde("{\"flood_probability\":0.5,\"risk_level\":\"LOW\",\"model_version\":\"v\","
                + "\"top_contributors\":[{\"feature_id\":\"river_level\"}]}");
        assertUnknown(predizEConfere(), DegradationReason.AI_ERROR);
    }
}
