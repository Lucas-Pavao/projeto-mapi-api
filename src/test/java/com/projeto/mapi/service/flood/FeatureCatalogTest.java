package com.projeto.mapi.service.flood;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureCatalogTest {

    private final FeatureCatalog catalog = new FeatureCatalog();

    @Test
    void rotulosEmPortuguesParaIdsConhecidos() {
        assertThat(catalog.label("tide_height_t1")).isEqualTo("Maré prevista na próxima hora");
        assertThat(catalog.label("river_level")).isEqualTo("Nível do rio");
        assertThat(catalog.label("rainfall_3h_accumulated")).isEqualTo("Chuva acumulada em 3 horas");
    }

    @Test
    void cobreTodasAsFeaturesDoProjetoMapiAi() {
        List<String> aiFeatures = List.of("latitude", "longitude", "current_rainfall", "rainfall_3h_accumulated",
                "rainfall_6h_accumulated", "rainfall_12h_accumulated", "rainfall_24h_accumulated", "tide_level",
                "river_level", "altitude", "distance_to_channel", "distance_to_flood_point",
                "distance_to_rain_sensor", "distance_to_river_sensor", "wind_speed", "temperature",
                "apparent_temperature", "humidity", "pressure", "wave_height", "wave_period", "wave_direction",
                "solar_radiation", "hour_sin", "hour_cos", "month_sin", "month_cos", "is_high_tide");
        assertThat(aiFeatures).allMatch(catalog::contains);
    }

    @Test
    void cobreTermosDoGlossario() {
        assertThat(List.of("street_elevation", "outlet_sill_elevation", "outside_level", "sill_clearance",
                "contributing_area", "water_depth", "tide_residual")).allMatch(catalog::contains);
    }

    @Test
    void idDesconhecidoViraOutroFatorEGeraWarn() {
        Logger logger = (Logger) LoggerFactory.getLogger(FeatureCatalog.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(catalog.label("feature_inexistente_xyz")).isEqualTo("Outro fator");
            assertThat(appender.list).anySatisfy(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.WARN);
                assertThat(e.getFormattedMessage()).contains("feature_inexistente_xyz");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void contribuinteEImputadaUsamOCatalogo() {
        assertThat(catalog.contributor("zzz", 0.1).label()).isEqualTo("Outro fator");
        assertThat(catalog.imputed(List.of("river_level", "river_level"))).hasSize(1);
        assertThat(catalog.imputed(null)).isEmpty();
    }
}
