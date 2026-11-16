package com.projeto.mapi.integration;

import com.projeto.mapi.service.geocoding.GeocodeCacheService;
import com.projeto.mapi.service.geocoding.GeocodeLevel;
import com.projeto.mapi.service.geocoding.GeocodeResult;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Geocodificação reprodutível (V28): endereço já visto devolve sempre a mesma coordenada. */
@org.springframework.test.context.TestPropertySource(properties = "mapi.ingestion.geocode-cache.enabled=true")
class GeocodeCacheIntegrationTest extends AbstractTimescaleIntegrationTest {

    @Autowired GeocodeCacheService cache;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM geocode_cache");
    }

    @Test
    void enderecoJaVistoNaoVoltaAoGeocodificador_mesmoQueEleMudeAResposta() {
        AtomicInteger calls = new AtomicInteger();
        Optional<GeocodeResult> a = cache.resolve("rua da aurora 100", "Boa Vista", "Recife", () -> {
            calls.incrementAndGet();
            return Optional.of(new GeocodeResult(-8.06, -34.88, GeocodeLevel.STREET));
        });
        // segunda carga: o geocodificador devolveria outra coordenada (não reprodutível); vale a do cache
        Optional<GeocodeResult> b = cache.resolve("RUA DA AURORA  100", "boa vista", "recife", () -> {
            calls.incrementAndGet();
            return Optional.of(new GeocodeResult(-8.10, -34.95, GeocodeLevel.NEIGHBORHOOD));
        });
        assertThat(calls.get()).isEqualTo(1);
        assertThat(b.orElseThrow().lat()).isEqualTo(a.orElseThrow().lat());
        assertThat(b.get().lon()).isEqualTo(-34.88);
        assertThat(b.get().level()).isEqualTo(GeocodeLevel.STREET);
        assertThat(jdbc.queryForObject("SELECT provider || '|' || version || '|' || active FROM geocode_cache", String.class))
                .isEqualTo("NOMINATIM|1|true");
    }

    @Test
    void falhaDoGeocodificadorNaoEntraNoCache_eTentaDeNovo() {
        assertThat(cache.resolve("rua x", "", "Recife", Optional::empty)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM geocode_cache", Long.class)).isZero();
        assertThat(cache.resolve("rua x", "", "Recife", () -> Optional.of(new GeocodeResult(-8.0, -34.9, null)))).isPresent();
    }
}
