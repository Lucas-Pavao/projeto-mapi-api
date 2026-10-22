package com.projeto.mapi.service.desempenho;

import com.projeto.mapi.dto.BucketSimulationDTO;
import com.projeto.mapi.exception.ServiceUnavailableException;
import com.projeto.mapi.exception.UnprocessableEntityException;
import com.projeto.mapi.service.desempenho.impl.BucketSimulationClientImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Cliente de POST /v1/simulate/bucket (contrato real da IA, commit a3bfa9c do projeto-mapi-ai), com a IA simulada. */
class BucketSimulationClientImplTest {

    private MockRestServiceServer server;
    private BucketSimulationClientImpl client;

    private static final BucketSimulationClient.Point PONTO = new BucketSimulationClient.Point("p1", 12000.0,
            List.of(0.0, 0.05, 0.10), List.of(0.0, 4.0, 9.5), 0.15);

    @BeforeEach
    void setUp() {
        RestClient.Builder b = RestClient.builder().baseUrl("http://ai.local");
        server = MockRestServiceServer.bindTo(b).build();
        client = new BucketSimulationClientImpl(b.build());
    }

    @Test
    void enviaSnakeCaseSemCamposFixos_eMapeiaParaCamelCaseComTCross0Based() {
        server.expect(requestTo("http://ai.local/v1/simulate/bucket")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.P_mm_h[0]").value(10.0))
                .andExpect(jsonPath("$.P_mm_h.length()").value(3))
                .andExpect(jsonPath("$.point.slug").value("p1"))
                .andExpect(jsonPath("$.point.area_m2").value(12000.0))
                .andExpect(jsonPath("$.point.z_curva_m[1]").value(0.05))
                .andExpect(jsonPath("$.point.v_curva_m3[2]").value(9.5))
                .andExpect(jsonPath("$.point.rua_rel_m").value(0.15))
                .andExpect(jsonPath("$.params.f").value(0.6))
                .andExpect(jsonPath("$.params.curve_scale").value(1.1))
                // C, iT, hcrit e snap_m são fixos na IA: enviar daria 422 (extra=forbid)
                .andExpect(jsonPath("$.params.C").doesNotExist())
                .andExpect(jsonPath("$.params.iT_mm_h").doesNotExist())
                .andExpect(jsonPath("$.params.hcrit_mm").doesNotExist())
                .andExpect(jsonPath("$.params.snap_m").doesNotExist())
                .andRespond(withSuccess("""
                        {"h_mm":[0.0,10.5,160.0],"n_fora_da_curva":1,"hmax_mm":160.0,"t_cross_h":2,
                         "params_usados":{"C":0.86,"iT_mm_h":60.1,"f":0.6,"hcrit_mm":150.0,"snap_m":10,"curve_scale":1.1},
                         "model":"M1a","illustrative":true}""", MediaType.APPLICATION_JSON));

        BucketSimulationDTO r = client.simulate(List.of(10.0, 20.0, 30.0), PONTO, 0.6, 1.1);

        assertThat(r.model()).isEqualTo("M1a");
        assertThat(r.illustrative()).isTrue();
        assertThat(r.hMm()).containsExactly(0.0, 10.5, 160.0);
        assertThat(r.hmaxMm()).isEqualTo(160.0);
        assertThat(r.tCrossH()).as("índice 0-based em rainfallMmH").isEqualTo(2);
        assertThat(r.nOutsideCurve()).isEqualTo(1);
        assertThat(r.paramsUsed().c()).isEqualTo(0.86);
        assertThat(r.paramsUsed().iTmmH()).isEqualTo(60.1);
        assertThat(r.paramsUsed().hcritMm()).isEqualTo(150.0);
        assertThat(r.paramsUsed().snapM()).isEqualTo(10);
        assertThat(r.paramsUsed().curveScale()).isEqualTo(1.1);
        server.verify();
    }

    @Test
    void tCrossNuloQuandoNaoCruza() {
        server.expect(requestTo("http://ai.local/v1/simulate/bucket")).andRespond(withSuccess("""
                {"h_mm":[0.0],"n_fora_da_curva":0,"hmax_mm":0.0,"t_cross_h":null,
                 "params_usados":{"C":0.86,"iT_mm_h":60.1,"f":1.0,"hcrit_mm":150.0,"snap_m":10,"curve_scale":1.0},
                 "model":"M1a","illustrative":true}""", MediaType.APPLICATION_JSON));
        assertThat(client.simulate(List.of(0.0), PONTO, 1.0, 1.0).tCrossH()).isNull();
    }

    @Test
    void ia422_viraUnprocessable() {
        server.expect(requestTo("http://ai.local/v1/simulate/bucket"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON).body("{\"detail\":[]}"));
        assertThatThrownBy(() -> client.simulate(List.of(1.0), PONTO, 1.0, 1.0)).isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void ia5xxOuForaDoAr_viraServiceUnavailable() {
        server.expect(requestTo("http://ai.local/v1/simulate/bucket")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> client.simulate(List.of(1.0), PONTO, 1.0, 1.0)).isInstanceOf(ServiceUnavailableException.class);
    }
}
