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
    void tokenInvalidoEm400TambemRenova() {
        assertThat(CemadenPedHttpClient.tokenRejected(400, "Alerta\nO Token informado é inválido!")).isTrue();
        assertThat(CemadenPedHttpClient.tokenRejected(401, "")).isTrue();
        assertThat(CemadenPedHttpClient.tokenRejected(400, "parâmetro inicio inválido")).isFalse();
        assertThat(CemadenPedHttpClient.tokenRejected(500, "token")).isFalse();
    }
}
