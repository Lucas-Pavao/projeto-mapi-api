package com.projeto.mapi.integration;

import com.projeto.mapi.config.PointRainProperties;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.export.DataExportService;
import com.projeto.mapi.service.tide.TideService;
import com.projeto.mapi.service.geocoding.GeocodingService;
import com.projeto.mapi.support.AbstractIngestionTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/** Integração do export estratificado (V32, ADR 0025): estratos Ouro/Prata/Bronze, TEMPORAL_DAY_ONLY e rain_tier. */
@TestPropertySource(properties = {
        "mapi.export.label.stratified.enabled=true",
        "mapi.export.label.linkage-radius-m=600"
})
class StratifiedExportIntegrationTest extends AbstractIngestionTest {

    private static final LocalDateTime END = LocalDateTime.of(2026, 5, 10, 0, 0);

    @Autowired DataExportService export;
    @Autowired FloodPointRepository points;
    @Autowired PointRainProperties pointRainProps;
    @MockitoBean TideService tide;
    @MockitoBean GeocodingService geocoding;

    private long cfgStrat;
    private UUID run;

    @BeforeEach
    void seed() {
        when(tide.getStoredTideHeightAt(anyDouble(), anyDouble(), any(LocalDateTime.class))).thenReturn(1.0);
        limparDependentesDePontos();
        jdbc.execute("DELETE FROM occurrence_cluster_member");
        jdbc.execute("DELETE FROM occurrence_cluster");
        jdbc.execute("DELETE FROM occurrence_point_match");
        jdbc.execute("DELETE FROM flood_occurrence");
        jdbc.execute("DELETE FROM point_rain_hourly");
        points.deleteAll();
        cfgStrat = jdbc.queryForObject(
                "SELECT id FROM linkage_config WHERE kind = 'OCCURRENCE_POINT' AND method = 'STRATIFIED_TIER'",
                Long.class);
        run = UUID.randomUUID();
        jdbc.update("INSERT INTO ingestion_run (id, source, kind, mode, code_version, parser_version, rule_version, started_at, status)"
                + " VALUES (?, 'CKAN_RECIFE', 'BACKFILL', 'REFETCH', 't', 't', 't', now(), 'OK')", run);
    }

    @Test
    void allowReanalysisFallbackEhFalsePorPadrao_eExportaColunasEstratificadas() {
        assertThat(pointRainProps.isAllowReanalysisFallback()).isFalse();

        long ptId = points.save(FloodPoint.builder().slug("pa-strat").name("Ponto Strat")
                .latitude(-8.05).longitude(-34.88).build()).getId();

        // Chuva medida: 18:00Z primária (1,2 km), 19:00Z estendida (4,2 km)
        long spCfg = jdbc.queryForObject("SELECT id FROM linkage_config WHERE kind = 'SENSOR_POINT' LIMIT 1", Long.class);
        jdbc.update("""
                INSERT INTO point_rain_hourly (flood_point_id, hour_end_utc, value, source, station_id, station_distance_m,
                  n_candidates, n_valid, n_valid_with_value, linkage_config_id, rule_version, rain_tier, rain_confidence)
                VALUES (?, '2026-05-08T18:00:00Z'::timestamptz, 25.0, 'CEMADEN_PED', 'E1', 1200.0, 1, 1, 1, ?, 'chuva-ponto-v4', 'MEASURED_PRIMARY', 1.0),
                       (?, '2026-05-08T19:00:00Z'::timestamptz, 12.0, 'CEMADEN_PED', 'E2', 4200.0, 1, 1, 1, ?, 'chuva-ponto-v4', 'MEASURED_EXTENDED', 0.85)
                """, ptId, spCfg, ptId, spCfg);

        // Ocorrência Ouro (ADDRESS, 150 m, HOUR às 19:00Z -> dentro de [18-2h, 18+6h])
        long occGold = jdbc.queryForObject("""
                INSERT INTO flood_occurrence (ckan_resource_id, ckan_record_id, raw_hash, run_id, occurred_from, occurred_to,
                  temporal_precision, geocode_method, geocode_level)
                VALUES ('r', 'g1', 'h1', ?, '2026-05-08T19:00:00Z'::timestamptz, '2026-05-08T19:00:01Z'::timestamptz,
                  'HOUR', 'GEOCODING_SERVICE', 'ADDRESS') RETURNING id
                """, Long.class, run);
        jdbc.update("""
                INSERT INTO occurrence_point_match (occurrence_id, flood_point_id, distance_m, radius_m, linkage_config_id, rule_version, matched_at)
                VALUES (?, ?, 150.0, 600.0, ?, 't', now())
                """, occGold, ptId, cfgStrat);

        // Ocorrência Bronze DAY (NEIGHBORHOOD, 500 m, cluster=3 no dia 09 -> TEMPORAL_DAY_ONLY)
        long occBronze = jdbc.queryForObject("""
                INSERT INTO flood_occurrence (ckan_resource_id, ckan_record_id, raw_hash, run_id, occurred_from, occurred_to,
                  temporal_precision, geocode_method, geocode_level)
                VALUES ('r', 'b1', 'h2', ?, '2026-05-09T03:00:00Z'::timestamptz, '2026-05-09T03:00:00Z'::timestamptz + interval '1 day',
                  'DAY', 'GEOCODING_SERVICE', 'NEIGHBORHOOD') RETURNING id
                """, Long.class, run);
        long clusterId = jdbc.queryForObject("""
                INSERT INTO occurrence_cluster (local_day, event_type, temporal_precision, spatial_precision, confidence,
                  first_from, sources, members, rule_version)
                VALUES ('2026-05-09', 'ALAGAMENTO', 'DAY', 'BAIRRO', 'BAIXA', '2026-05-09T03:00:00Z'::timestamptz,
                  ARRAY['CKAN_RECIFE'], 3, 'cluster-v1') RETURNING id
                """, Long.class);
        jdbc.update("INSERT INTO occurrence_cluster_member (cluster_id, occurrence_id) VALUES (?, ?)", clusterId, occBronze);
        jdbc.update("""
                INSERT INTO occurrence_point_match (occurrence_id, flood_point_id, distance_m, radius_m, linkage_config_id, rule_version, matched_at)
                VALUES (?, ?, 500.0, 600.0, ?, 't', now())
                """, occBronze, ptId, cfgStrat);

        var rows = export.exportUnifiedDataWithAccumulated("pa-strat", 3, END);
        var h18 = rows.stream().filter(r -> r.getTimestampUtc().toString().equals("2026-05-08T18:00:00Z")).findFirst().orElseThrow();
        var h19 = rows.stream().filter(r -> r.getTimestampUtc().toString().equals("2026-05-08T19:00:00Z")).findFirst().orElseThrow();
        var d09 = rows.stream().filter(r -> r.getTimestampUtc().toString().equals("2026-05-09T06:00:00Z")).findFirst().orElseThrow();

        assertThat(h18.getRainTier()).isEqualTo("MEASURED_PRIMARY");
        assertThat(h18.getRainConfidence()).isEqualTo(1.0);
        assertThat(h18.getLabelByHorizon().get(6)).isEqualTo(1);
        assertThat(h18.getLabelTier()).isEqualTo("HIGH");
        assertThat(h18.getSampleWeight()).isEqualTo(1.0);
        assertThat(h18.getTemporalMatch()).isEqualTo("EVENT_WINDOW");

        assertThat(h19.getRainTier()).isEqualTo("MEASURED_EXTENDED");
        assertThat(h19.getRainConfidence()).isEqualTo(0.85);

        assertThat(d09.getLabelTier()).isEqualTo("LOW");
        assertThat(d09.getSampleWeight()).isEqualTo(0.4);
        assertThat(d09.getTemporalMatch()).isEqualTo("TEMPORAL_DAY_ONLY");

        var csvExport = export.exportCsv("pa-strat", 3, END);
        assertThat(csvExport.csv().split("\n")[0]).contains(",tier,sample_weight,temporal_match,rain_tier,rain_confidence");
        assertThat(csvExport.manifest().label().stratifiedCounts())
                .containsEntry("HIGH", 1)
                .containsEntry("LOW", 1)
                .containsEntry("TEMPORAL_DAY_ONLY", 1);
    }
}
