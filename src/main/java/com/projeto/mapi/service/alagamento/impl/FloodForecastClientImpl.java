package com.projeto.mapi.service.alagamento.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.repository.analise.RainForecastRepository.OmHour;
import com.projeto.mapi.repository.analise.RainForecastRepository.Reading;
import com.projeto.mapi.service.alagamento.FloodForecastClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cliente de {@code POST /v1/flood/forecast} (snake_case). Leitura sem valor vai nula, nunca zero. */
@Service
@Slf4j
public class FloodForecastClientImpl implements FloodForecastClient {

    private final RestClient restClient;

    @Autowired
    public FloodForecastClientImpl(@Value("${app.ai.api-url:http://localhost:8000}") String aiApiUrl, ExternalRestClientFactory http) {
        this(http.builder("ai").baseUrl(aiApiUrl).build());
    }

    public FloodForecastClientImpl(RestClient restClient) {
        this.restClient = restClient;
    }

    static Map<String, Object> body(double lat, double lon, List<Reading> readings, List<OmHour> openMeteo, List<Double> rain10min,
                                    Terrain t, String terrainStatus, double trYears, double teMinutes) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("latitude", lat);
        b.put("longitude", lon);
        List<Map<String, Object>> rd = new ArrayList<>(readings.size());
        for (Reading r : readings) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("station_id", r.stationId());
            m.put("latitude", r.lat());
            m.put("longitude", r.lon());
            m.put("ts", r.ts().toString());
            m.put("mm", r.mm());
            rd.add(m);
        }
        b.put("readings", rd);
        if (openMeteo != null && !openMeteo.isEmpty()) {
            b.put("open_meteo", openMeteo.stream().map(o -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("hour_end_utc", o.hourEndUtc().toString());
                m.put("mm", o.mm());
                return m;
            }).toList());
        }
        b.put("rain_10min_mm", rain10min);
        if (t != null) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("slug", t.slug());
            p.put("area_m2", t.areaM2());
            p.put("z_curva_m", t.zCurveM());
            p.put("v_curva_m3", t.vCurveM3());
            p.put("rua_rel_m", t.streetRelM());
            b.put("point", p);
        }
        b.put("terrain_status", terrainStatus);
        b.put("tr_anos", trYears);
        b.put("te_min", teMinutes);
        return b;
    }

    @Override
    public JsonNode forecast(double lat, double lon, List<Reading> readings, List<OmHour> openMeteo, List<Double> rain10min,
                             Terrain terrain, String terrainStatus, double trYears, double teMinutes) {
        try {
            return restClient.post().uri("/v1/flood/forecast").contentType(MediaType.APPLICATION_JSON)
                    .body(body(lat, lon, readings, openMeteo, rain10min, terrain, terrainStatus, trYears, teMinutes))
                    .retrieve().body(JsonNode.class);
        } catch (Exception e) {
            log.warn("Alerta por regressão indisponível ({}): {}", terrain == null ? "sem terreno" : terrain.slug(), e.toString());
            return null;
        }
    }
}
