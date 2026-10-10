package com.projeto.mapi.service.ingestion.adapter;

import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.exception.SourceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP do ERA5-Land no Open-Meteo archive. Reaproveita o cliente/timeouts "open-meteo-archive". Uso gratuito é
 * não comercial; a página oficial não declara rate limit (F2-BACK-15), então a coleta é sequencial por ponto.
 */
@Component
@RequiredArgsConstructor
public class Era5LandHttpClient implements Era5LandClient {

    static final String HOURLY = "soil_moisture_0_to_7cm";
    static final String MODEL = "era5_land";

    private final ExternalRestClientFactory http;
    private final IngestionProperties props;

    /** timezone=GMT e models=era5_land explícitos: nunca depender dos padrões da API. */
    @Override
    public Response fetch(double lat, double lon, LocalDate start, LocalDate end) {
        String url = String.format(Locale.ROOT,
                "%s/archive?latitude=%s&longitude=%s&start_date=%s&end_date=%s&hourly=%s&models=%s&timezone=GMT",
                props.getOpenMeteoArchiveUrl(), lat, lon, start, end, HOURLY, MODEL);
        try {
            String body = http.client("open-meteo-archive", props.getOpenMeteoArchiveUrl(), Map.of())
                    .get()
                    .uri(uriBuilder -> uriBuilder.path("/archive")
                            .queryParam("latitude", lat).queryParam("longitude", lon)
                            .queryParam("start_date", start).queryParam("end_date", end)
                            .queryParam("hourly", HOURLY).queryParam("models", MODEL)
                            .queryParam("timezone", "GMT").build())
                    .retrieve().body(String.class);
            if (body == null || body.isBlank()) throw new SourceUnavailableException("ERA5-Land: corpo vazio");
            return new Response(url, body);
        } catch (SourceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SourceUnavailableException("ERA5-Land (Open-Meteo archive) indisponível: " + e.getMessage(), e);
        }
    }
}
