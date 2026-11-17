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

    record AiRequest(@JsonProperty("rain_10min_mm") List<Double> rain, AiPoint point, @JsonProperty("tr_anos") double tr,
                     @JsonProperty("te_min") double te, @JsonProperty("forecast_1h_q50_mm") Double q50,
                     @JsonProperty("forecast_1h_q80_mm") Double q80) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiProj(@JsonProperty("h_mm") List<Double> h, @JsonProperty("minutes_to_hcrit") Integer minutes,
                  @JsonProperty("h_max_mm") double hMax) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiResponse(String version, @JsonProperty("h_now_mm") double hNow, Map<String, AiProj> projection,
                      @JsonProperty("i_cap_mm_h") double iCap, @JsonProperty("runtime_ms") double runtimeMs) {}

    @Override
    public Result evaluate(List<Double> rain10min, Terrain t, double trYears, double teMinutes, Double q50Mm, Double q80Mm) {
        try {
            AiResponse r = restClient.post().uri("/v1/bucket/point10").contentType(MediaType.APPLICATION_JSON)
                    .body(new AiRequest(rain10min, new AiPoint(t.slug(), t.areaM2(), t.zCurveM(), t.vCurveM3(), t.streetRelM()),
                            trYears, teMinutes, q50Mm, q80Mm))
                    .retrieve().body(AiResponse.class);
            if (r == null) return null;
            return new Result(r.version(), r.hNow(), proj(r, "q50"), proj(r, "q80"), r.iCap(), r.runtimeMs());
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
