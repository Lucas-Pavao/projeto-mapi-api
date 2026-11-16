package com.projeto.mapi.service.flood.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.repository.analise.RainForecastRepository.OmHour;
import com.projeto.mapi.repository.analise.RainForecastRepository.Reading;
import com.projeto.mapi.service.flood.RainForecastClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Cliente da previsão de chuva da IA (snake_case). Leitura sem valor vai como nula, nunca como zero. */
@Service
@Slf4j
public class RainForecastClientImpl implements RainForecastClient {

    private final RestClient restClient;

    @Autowired
    public RainForecastClientImpl(@Value("${app.ai.api-url:http://localhost:8000}") String aiApiUrl,
                                  ExternalRestClientFactory http) {
        this(http.builder("ai").baseUrl(aiApiUrl).build());
    }

    /** Usado pelos testes com servidor simulado. */
    public RainForecastClientImpl(RestClient restClient) {
        this.restClient = restClient;
    }

    record AiReading(@JsonProperty("station_id") String stationId, double latitude, double longitude, String ts, Double mm) {}

    record AiOm(@JsonProperty("hour_end_utc") String hourEndUtc, Double mm) {}

    record AiRequest(double latitude, double longitude, List<AiReading> readings,
                     @JsonProperty("open_meteo") List<AiOm> openMeteo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiHorizon(@JsonProperty("horizon_hours") int horizonHours, @JsonProperty("valid_until") Instant validUntil,
                     @JsonProperty("quantiles_mm") Map<String, Double> quantiles,
                     @JsonProperty("prob_media") double probMedia, @JsonProperty("prob_forte") double probForte,
                     @JsonProperty("forte_alert") boolean forteAlert, String model) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiResponse(@JsonProperty("model_version") String modelVersion, @JsonProperty("issued_at") Instant issuedAt,
                      List<AiHorizon> horizons, List<String> sources,
                      @JsonProperty("missing_inputs") List<String> missingInputs,
                      @JsonProperty("latest_reading_utc") Instant latestReadingUtc,
                      @JsonProperty("latest_reading_age_minutes") Double latestReadingAgeMinutes) {}

    @Override
    public Optional<Result> forecast(double lat, double lon, List<Reading> readings, List<OmHour> openMeteo) {
        AiRequest body = new AiRequest(lat, lon,
                readings.stream().map(r -> new AiReading(r.stationId(), r.lat(), r.lon(), r.ts().toString(), r.mm())).toList(),
                openMeteo == null || openMeteo.isEmpty() ? null
                        : openMeteo.stream().map(o -> new AiOm(o.hourEndUtc().toString(), o.mm())).toList());
        try {
            AiResponse ai = restClient.post().uri("/v1/forecast/rain").contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(AiResponse.class);
            if (ai == null || ai.horizons() == null || ai.issuedAt() == null) {
                log.error("Resposta vazia da previsão de chuva");
                return Optional.empty();
            }
            return Optional.of(new Result(ai.modelVersion(), ai.issuedAt(), ai.horizons().stream()
                    .map(h -> new Horizon(h.horizonHours(), h.validUntil(), h.quantiles().get("q50"), h.quantiles().get("q80"),
                            h.quantiles().get("q95"), h.probMedia(), h.probForte(), h.forteAlert(), h.model())).toList(),
                    ai.sources() == null ? List.of() : ai.sources(),
                    ai.missingInputs() == null ? List.of() : ai.missingInputs(), ai.latestReadingUtc(),
                    ai.latestReadingAgeMinutes()));
        } catch (HttpClientErrorException e) {
            log.warn("IA recusou a previsão de chuva ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Falha ao chamar a previsão de chuva da IA: {}", e.toString());
            return Optional.empty();
        }
    }
}
