package com.projeto.mapi.service.ingestion.adapter.ped;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** Validade do token da PED: exp do JWT e momento de renovar. */
class CemadenPedHttpClientTest {

    private static final Instant NOW = Instant.parse("2026-11-09T13:51:00Z");

    @Test
    void leExpDoJwt() {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"exp\":1794232509}".getBytes(StandardCharsets.UTF_8));
        assertThat(CemadenPedHttpClient.expiryOf("h." + payload + ".s", NOW)).isEqualTo(Instant.ofEpochSecond(1794232509));
        assertThat(CemadenPedHttpClient.expiryOf("lixo", NOW)).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void tokenRepetidoDentroDaMargemEhUsadoAteVencer() {
        Instant exp = Instant.parse("2026-11-09T13:55:09Z");
        // emitido às 13:51 com vencimento em 4 min: não pede de novo a cada requisição, só depois de vencer
        assertThat(CemadenPedHttpClient.renewAtFor(exp, NOW)).isEqualTo(exp.plusSeconds(1));
        // token novo, com 1 h de validade: renova 5 min antes
        assertThat(CemadenPedHttpClient.renewAtFor(exp.plusSeconds(3600), NOW))
                .isEqualTo(exp.plusSeconds(3600).minusSeconds(300));
    }
}
