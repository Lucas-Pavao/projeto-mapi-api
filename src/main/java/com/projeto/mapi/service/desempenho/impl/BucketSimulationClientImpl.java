package com.projeto.mapi.service.desempenho.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.dto.BucketSimulationDTO;
import com.projeto.mapi.exception.ServiceUnavailableException;
import com.projeto.mapi.exception.UnprocessableEntityException;
import com.projeto.mapi.service.desempenho.BucketSimulationClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Chama a IA em snake_case e devolve o contrato camelCase do backend. O backend nunca envia C, iT, hcrit nem snap_m
 * (a IA responde 422 a esses campos, extra=forbid).
 */
@Service
@Slf4j
public class BucketSimulationClientImpl implements BucketSimulationClient {

    private final RestClient restClient;

    @Autowired
    public BucketSimulationClientImpl(@Value("${app.ai.api-url:http://localhost:8000}") String aiApiUrl,
                                      ExternalRestClientFactory http) {
        this(http.builder("ai").baseUrl(aiApiUrl).build());
    }

    /** Usado pelos testes com servidor simulado. */
    public BucketSimulationClientImpl(RestClient restClient) {
        this.restClient = restClient;
    }

    record AiPoint(String slug, @JsonProperty("area_m2") double areaM2,
                   @JsonProperty("z_curva_m") List<Double> zCurvaM,
                   @JsonProperty("v_curva_m3") List<Double> vCurvaM3,
                   @JsonProperty("rua_rel_m") double ruaRelM) {
    }

    record AiParams(double f, @JsonProperty("curve_scale") double curveScale) {
    }

    record AiRequest(@JsonProperty("P_mm_h") List<Double> rainfall, AiPoint point, AiParams params) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiResponse(@JsonProperty("h_mm") List<Double> hMm,
                      @JsonProperty("n_fora_da_curva") int nForaDaCurva,
                      @JsonProperty("hmax_mm") double hmaxMm,
                      @JsonProperty("t_cross_h") Integer tCrossH,
                      @JsonProperty("params_usados") AiParamsUsed paramsUsados,
                      String model,
                      boolean illustrative) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiParamsUsed(@JsonProperty("C") double c, @JsonProperty("iT_mm_h") double iT,
                        @JsonProperty("f") double f, @JsonProperty("hcrit_mm") double hcrit,
                        @JsonProperty("snap_m") int snapM, @JsonProperty("curve_scale") double curveScale) {
    }

    @Override
    public BucketSimulationDTO simulate(List<Double> rainfallMmH, Point point, double f, double curveScale) {
        AiRequest body = new AiRequest(rainfallMmH,
                new AiPoint(point.slug(), point.areaM2(), point.zCurvaM(), point.vCurvaM3(), point.ruaRelM()),
                new AiParams(f, curveScale));
        AiResponse ai;
        try {
            ai = restClient.post().uri("/v1/simulate/bucket").contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(AiResponse.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
                log.warn("IA recusou a simulação do ponto {} (422): {}", point.slug(), e.getResponseBodyAsString());
                throw new UnprocessableEntityException("A IA recusou os parâmetros da simulação");
            }
            log.error("IA respondeu {} à simulação do ponto {}", e.getStatusCode(), point.slug());
            throw new ServiceUnavailableException("A IA não pôde simular o balde", e);
        } catch (Exception e) {
            log.error("Falha ao chamar a IA para simular o ponto {}: {}", point.slug(), e.toString());
            throw new ServiceUnavailableException("IA indisponível", e);
        }
        if (ai == null || ai.hMm() == null || ai.paramsUsados() == null) {
            throw new ServiceUnavailableException("Resposta vazia ou incompleta da IA");
        }
        AiParamsUsed p = ai.paramsUsados();
        return new BucketSimulationDTO(ai.model() == null ? "M1a" : ai.model(), true, ai.hMm(), ai.hmaxMm(),
                ai.tCrossH(), ai.nForaDaCurva(),
                new BucketSimulationDTO.ParamsUsed(p.c(), p.iT(), p.f(), p.hcrit(), p.snapM(), p.curveScale()));
    }
}
