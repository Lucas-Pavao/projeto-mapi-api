package com.projeto.mapi.service.weather.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.weather.OpenMeteoModelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OpenMeteoModelClientImpl implements OpenMeteoModelClient {

    /** Nome do modelo na API de previsão -> nome no meta.json ({@code /data/<nome>/static/meta.json}). */
    static final Map<String, String> META_NAME = Map.of(
            "ecmwf_ifs", "ecmwf_ifs",
            "ecmwf_ifs025", "ecmwf_ifs025",
            "icon_global", "dwd_icon",
            // ICON na grade nativa: o que o DWD mantém depois de 30/11/2026 (https://open-meteo.com/en/docs/dwd-api, 22/11/2026)
            "dwd_icon_global_native", "dwd_icon_global_native",
            "gfs_seamless", "ncep_gfs013");

    private final RestClient forecast;
    private final RestClient previous;
    private final ObjectMapper mapper;

    public OpenMeteoModelClientImpl(
            @Value("${app.weather.api-url:https://api.open-meteo.com/v1}") String forecastUrl,
            @Value("${app.weather.previous-runs-url:https://previous-runs-api.open-meteo.com/v1}") String previousUrl,
            ExternalRestClientFactory http, ObjectMapper mapper) {
        this.forecast = http.builder("open-meteo").baseUrl(forecastUrl).build();
        this.previous = http.builder("open-meteo-archive").baseUrl(previousUrl).build();
        this.mapper = mapper;
    }

    @Override
    public Optional<RunMeta> runMeta(String model) {
        String meta = META_NAME.get(model);
        if (meta == null) return Optional.empty();
        // meta.json fica fora de /v1: https://api.open-meteo.com/data/<modelo>/static/meta.json
        String body = forecast.get().uri(u -> u.replacePath("/data/" + meta + "/static/meta.json").build())
                .retrieve().body(String.class);
        try {
            JsonNode n = mapper.readTree(body);
            long init = n.path("last_run_initialisation_time").asLong(0);
            long avail = n.path("last_run_availability_time").asLong(0);
            if (init <= 0 || avail <= 0) throw new IllegalStateException("meta.json sem instante da rodada: " + model);
            return Optional.of(new RunMeta(Instant.ofEpochSecond(init), Instant.ofEpochSecond(avail)));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("meta.json ilegível (" + model + "): " + e.getMessage(), e);
        }
    }

    @Override
    public Batch liveRun(double lat, double lon, String model) {
        String body = forecast.get().uri(u -> u.path("/forecast").queryParam("latitude", lat).queryParam("longitude", lon)
                        .queryParam("hourly", "precipitation,cape").queryParam("models", model)
                        .queryParam("forecast_days", 3).queryParam("timezone", "UTC").build())
                .retrieve().body(String.class);
        return parse(body, "precipitation", "cape");
    }

    @Override
    public Map<Integer, Batch> previousRuns(double lat, double lon, String model, LocalDate start, LocalDate end,
                                            List<Integer> leads, boolean withCape) {
        List<String> vars = new ArrayList<>();
        for (int n : leads) {
            vars.add("precipitation_previous_day" + n);
            if (withCape) vars.add("cape_previous_day" + n);
        }
        String body = previous.get().uri(u -> u.path("/forecast").queryParam("latitude", lat).queryParam("longitude", lon)
                        .queryParam("hourly", String.join(",", vars)).queryParam("models", model)
                        .queryParam("start_date", start).queryParam("end_date", end).queryParam("timezone", "UTC").build())
                .retrieve().body(String.class);
        Map<Integer, Batch> out = new LinkedHashMap<>();
        for (int n : leads) {
            out.put(n, parse(body, "precipitation_previous_day" + n, withCape ? "cape_previous_day" + n : null));
        }
        return out;
    }

    Batch parse(String body, String precipVar, String capeVar) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root.path("error").asBoolean(false)) {
                throw new IllegalStateException("Open-Meteo recusou: " + root.path("reason").asText());
            }
            if (root.path("utc_offset_seconds").asInt(-1) != 0) {
                throw new IllegalStateException("Open-Meteo respondeu fora de UTC");
            }
            JsonNode time = root.path("hourly").path("time");
            JsonNode p = root.path("hourly").path(precipVar);
            JsonNode c = capeVar == null ? null : root.path("hourly").path(capeVar);
            if (!p.isArray()) throw new IllegalStateException("Open-Meteo sem a variável " + precipVar);
            List<Hour> hours = new ArrayList<>();
            for (int i = 0; i < time.size(); i++) {
                Instant end = LocalDateTime.parse(time.get(i).asText()).toInstant(ZoneOffset.UTC);
                hours.add(new Hour(end, num(p.get(i)), c == null ? null : num(c.get(i))));
            }
            return new Batch(body, Hashing.sha256(body),
                    root.path("latitude").isNumber() ? root.path("latitude").asDouble() : null,
                    root.path("longitude").isNumber() ? root.path("longitude").asDouble() : null, hours);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Resposta do Open-Meteo ilegível: " + e.getMessage(), e);
        }
    }

    /** Valor ausente na origem fica ausente (nunca zero). */
    private static Double num(JsonNode n) {
        return n == null || n.isNull() ? null : n.asDouble();
    }
}
