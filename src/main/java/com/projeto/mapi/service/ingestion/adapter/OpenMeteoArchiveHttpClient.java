package com.projeto.mapi.service.ingestion.adapter;

import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.exception.SourceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/** HTTP do Open-Meteo archive (timeouts da fonte "open-meteo-archive"). Separado para ser trocado nos testes. */
@Component
@RequiredArgsConstructor
public class OpenMeteoArchiveHttpClient implements OpenMeteoArchiveClient {

    static final String HOURLY = "temperature_2m,relative_humidity_2m,surface_pressure,precipitation,"
            + "weather_code,wind_speed_10m,shortwave_radiation";

    private final ExternalRestClientFactory http;
    private final IngestionProperties props;

    /** timezone=GMT explícito (ADR 0006 seção 4): nunca depender do padrão da API. */
    @Override
    public Response fetch(double lat, double lon, LocalDate start, LocalDate end) {
        String url = String.format(Locale.ROOT,
                "%s/archive?latitude=%s&longitude=%s&start_date=%s&end_date=%s&hourly=%s&timezone=GMT",
                props.getOpenMeteoArchiveUrl(), lat, lon, start, end, HOURLY);
        try {
            String body = http.client("open-meteo-archive", props.getOpenMeteoArchiveUrl(), Map.of())
                    .get()
                    .uri(uriBuilder -> uriBuilder.path("/archive")
                            .queryParam("latitude", lat).queryParam("longitude", lon)
                            .queryParam("start_date", start).queryParam("end_date", end)
                            .queryParam("hourly", HOURLY).queryParam("timezone", "GMT").build())
                    .retrieve().body(String.class);
            if (body == null || body.isBlank()) throw new SourceUnavailableException("Open-Meteo archive: corpo vazio");
            return new Response(url, body);
        } catch (SourceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SourceUnavailableException("Open-Meteo archive indisponível: " + e.getMessage(), e);
        }
    }
}
