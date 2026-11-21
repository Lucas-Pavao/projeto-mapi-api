package com.projeto.mapi.service.alerta.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.service.alerta.PointBucketClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PointBucketClientImpl implements PointBucketClient {

    private final RestClient restClient;

    @Autowired
    public PointBucketClientImpl(@Value("${app.ai.api-url:http://localhost:8000}") String aiApiUrl, ExternalRestClientFactory http) {
        this(http.builder("ai").baseUrl(aiApiUrl).build());
    }

    public PointBucketClientImpl(RestClient restClient) {
        this.restClient = restClient;
    }

    record AiPoint(String slug, @JsonProperty("area_m2") double areaM2, @JsonProperty("z_curva_m") List<Double> z,
                   @JsonProperty("v_curva_m3") List<Double> v, @JsonProperty("rua_rel_m") double ruaRel) {}

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    record AiRequest(@JsonProperty("rain_10min_mm") List<Double> rain, AiPoint point, @JsonProperty("tr_anos") double tr,
                     @JsonProperty("te_min") double te, @JsonProperty("forecast_1h_q50_mm") Double q50,
                     @JsonProperty("forecast_1h_q80_mm") Double q80,
                     @JsonProperty("days_since_cleaning") Double days, @JsonProperty("lambda_re_per_day") Double lambda) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiCleaning(@JsonProperty("dias_desde_limpeza") double days, @JsonProperty("lambda_re_por_dia") double lambda,
                      @JsonProperty("p_entupida") double pClogged, @JsonProperty("h_now_entupida_mm") double hNow,
                      @JsonProperty("prob_hcrit_agora") Double probNow, @JsonProperty("prob_hcrit_60min_q50") Double prob60,
                      @JsonProperty("projection_entupida") Map<String, AiProj> projection) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiProj(@JsonProperty("h_mm") List<Double> h, @JsonProperty("minutes_to_hcrit") Integer minutes,
                  @JsonProperty("h_max_mm") double hMax) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiResponse(String version, @JsonProperty("h_now_mm") double hNow, Map<String, AiProj> projection,
                      @JsonProperty("i_cap_mm_h") double iCap, @JsonProperty("runtime_ms") double runtimeMs,
                      AiCleaning limpeza) {}

    @Override
    public Result evaluate(List<Double> rain10min, Terrain t, double trYears, double teMinutes, Double q50Mm, Double q80Mm,
                           Double daysSinceCleaning, Double lambdaRePerDay) {
        try {
            AiResponse r = restClient.post().uri("/v1/bucket/point10").contentType(MediaType.APPLICATION_JSON)
                    .body(new AiRequest(rain10min, new AiPoint(t.slug(), t.areaM2(), t.zCurveM(), t.vCurveM3(), t.streetRelM()),
                            trYears, teMinutes, q50Mm, q80Mm, daysSinceCleaning, lambdaRePerDay))
                    .retrieve().body(AiResponse.class);
            if (r == null) return null;
            Cleaning c = null;
            if (r.limpeza() != null) {
                AiProj pq = r.limpeza().projection() == null ? null : r.limpeza().projection().get("q50");
                c = new Cleaning(r.limpeza().days(), r.limpeza().lambda(), r.limpeza().pClogged(), r.limpeza().hNow(),
                        r.limpeza().probNow(), r.limpeza().prob60(), pq == null ? null : pq.minutes());
            }
            return new Result(r.version(), r.hNow(), proj(r, "q50"), proj(r, "q80"), r.iCap(), r.runtimeMs(), c);
        } catch (Exception e) {
            log.warn("Balde a 10 min indisponível para {}: {}", t.slug(), e.toString());
            return null;
        }
    }

    private static Projection proj(AiResponse r, String k) {
        AiProj p = r.projection() == null ? null : r.projection().get(k);
        return p == null ? null : new Projection(p.h(), p.minutes(), p.hMax());
    }
}
