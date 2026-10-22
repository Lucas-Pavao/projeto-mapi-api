package com.projeto.mapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.service.ingestion.live.LiveSensorStagingService;
import com.projeto.mapi.support.AbstractIngestionTest;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V22: cada leitura da CEMADEN é a chuva de um bloco de 10/60 min (carimbo no fim, rótulo em UTC-3) e a hora de
 * {@code observation_hourly_v} é a soma dos blocos. Bloco que falta numa hora com leitura conta como zero; hora sem
 * leitura não existe (lacuna). O lote ao vivo também grava a estação, com coordenadas.
 */
class CemadenChuvaPorBlocoIntegrationTest extends AbstractIngestionTest {

    @Autowired private LiveSensorStagingService staging;
    @Autowired private ObjectMapper mapper;

    private JsonNode block(String localLabel, double mm) {
        return mapper.createObjectNode().put("codigo", "261160610A").put("id_sensor", "APAC-PLUVIO-RECIFE-ALTO")
                .put("estacao_nome", "[CEMADEN] Alto Mandu").put("municipio", "RECIFE")
                .put("latitude", -8.019).put("longitude", -34.931)
                .put("data_hora", localLabel).put("chuva_acumulada", mm).put("fonte", "APAC/Cemaden");
    }

    private Map<String, Object> hour(String hourEndUtc) {
        return jdbc.queryForMap("SELECT value, n_readings, incomplete FROM observation_hourly_v WHERE source = 'CEMADEN' "
                + "AND station_id = '261160610A' AND variable = 'precipitation' AND hour_end_utc = ?::timestamptz", hourEndUtc);
    }

    @Test
    void horaESomaDosBlocos_semLeituraELacuna_eAEstacaoEGravada() {
        staging.stage("CEMADEN", List.of(
                // seco: bloco de 60 min às 17:20 local (20:20Z) => hora que termina 21:00Z vale 0,0
                block("2026-10-21 17:20:00", 0.0),
                // 18:xx local sem leitura (estação calada) => hora que termina 22:00Z não existe
                // chuva: três blocos de 10 min (faltam 20:00, 20:40, 20:50) => hora que termina 00:00Z = 0,39 + 0,2 + 0,2
                block("2026-10-21 20:10:00", 0.39), block("2026-10-21 20:20:00", 0.2), block("2026-10-21 20:30:00", 0.2),
                // borda: 21:00:00 local (00:00Z) fecha a hora anterior
                block("2026-10-21 21:00:00", 1.0)));

        Map<String, Object> dry = hour("2026-10-21T21:00:00Z");
        assertThat(dry.get("value")).isEqualTo(0.0);
        assertThat(dry.get("incomplete")).isEqualTo(false);

        assertThat(count("SELECT count(*) FROM observation_hourly_v WHERE source = 'CEMADEN' "
                + "AND hour_end_utc = '2026-10-21T22:00:00Z'::timestamptz")).isZero();

        Map<String, Object> rain = hour("2026-10-22T00:00:00Z");
        assertThat((Double) rain.get("value")).isCloseTo(0.39 + 0.2 + 0.2 + 1.0, Offset.offset(1e-9));
        assertThat(rain.get("n_readings")).isEqualTo(4);
        assertThat(rain.get("incomplete")).isEqualTo(false);

        Map<String, Object> station = jdbc.queryForMap(
                "SELECT name, municipality, lat, lon FROM station WHERE source = 'CEMADEN' AND station_id = '261160610A'");
        assertThat(station.get("name")).isEqualTo("[CEMADEN] Alto Mandu");
        assertThat(station.get("municipality")).isEqualTo("RECIFE");
        assertThat((Double) station.get("lat")).isEqualTo(-8.019);
    }

    @Test
    void reenviarAJanelaNaoDuplicaAHora() {
        List<JsonNode> window = List.of(block("2026-10-21 20:10:00", 0.39), block("2026-10-21 20:20:00", 0.2));
        staging.stage("CEMADEN", window);
        staging.stage("CEMADEN", window);
        assertThat((Double) hour("2026-10-22T00:00:00Z").get("value")).isCloseTo(0.59, Offset.offset(1e-9));
        assertThat(count("SELECT count(*) FROM observation WHERE source = 'CEMADEN'")).isEqualTo(2);
    }

    @Test
    void estacaoSemCoordenadaNoLoteNaoApagaAGravada() {
        staging.stage("CEMADEN", List.of(block("2026-10-21 20:10:00", 0.0)));
        JsonNode semCoord = mapper.createObjectNode().put("codigo", "261160610A").put("estacao_nome", "[CEMADEN] Alto Mandu")
                .put("municipio", "RECIFE").put("data_hora", "2026-10-21 20:20:00").put("chuva_acumulada", 0.0);
        staging.stage("CEMADEN", List.of(semCoord));
        assertThat(jdbc.queryForObject("SELECT lat FROM station WHERE source = 'CEMADEN' AND station_id = '261160610A'",
                Double.class)).isEqualTo(-8.019);
    }
}
