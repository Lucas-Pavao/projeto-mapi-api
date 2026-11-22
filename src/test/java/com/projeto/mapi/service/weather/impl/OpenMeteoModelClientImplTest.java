package com.projeto.mapi.service.weather.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.config.HttpTimeoutProperties;
import com.projeto.mapi.service.weather.OpenMeteoModelClient.Batch;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Leitura da resposta do Open-Meteo: só UTC, ausente fica ausente, erro da origem falha alto. */
class OpenMeteoModelClientImplTest {

    private final OpenMeteoModelClientImpl client = new OpenMeteoModelClientImpl("https://api.open-meteo.com/v1",
            "https://previous-runs-api.open-meteo.com/v1", new ExternalRestClientFactory(new HttpTimeoutProperties()),
            new ObjectMapper());

    @Test
    void horaTerminaNoCarimbo_ausenteFicaAusente() {
        String body = """
                {"latitude":-8.0,"longitude":-35.0,"utc_offset_seconds":0,
                 "hourly":{"time":["2026-11-22T10:00","2026-11-22T11:00"],"precipitation_previous_day1":[1.2,null],
                           "cape_previous_day1":[300,null]}}""";
        Batch b = client.parse(body, "precipitation_previous_day1", "cape_previous_day1");
        assertThat(b.hours()).hasSize(2);
        assertThat(b.hours().get(0).hourEndUtc()).isEqualTo(Instant.parse("2026-11-22T10:00:00Z"));
        assertThat(b.hours().get(1).precipMm()).isNull();
        assertThat(b.rawHash()).hasSize(64);
    }

    @Test
    void recusaRespostaForaDeUtcEErroDaOrigem() {
        assertThatThrownBy(() -> client.parse("{\"utc_offset_seconds\":-10800,\"hourly\":{\"time\":[],\"precipitation\":[]}}",
                "precipitation", null)).hasMessageContaining("UTC");
        assertThatThrownBy(() -> client.parse("{\"error\":true,\"reason\":\"Invalid value\"}", "precipitation", null))
                .hasMessageContaining("Invalid value");
    }
}
