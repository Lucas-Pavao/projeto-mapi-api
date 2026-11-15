package com.projeto.mapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V23 (ADR 0019): precisão e confiança derivadas por regra fixa no banco; grupos entre fontes; inventário. */
class LabelMultifonteIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    private UUID run;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM occurrence_cluster");
        jdbc.update("DELETE FROM occurrence_point_match WHERE occurrence_id IN (SELECT id FROM flood_occurrence WHERE ckan_resource_id LIKE 'TESTE-V23%')");
        jdbc.update("DELETE FROM flood_occurrence WHERE ckan_resource_id LIKE 'TESTE-V23%'");
        run = ObservationFixtures.run(jdbc, "CKAN_RECIFE");
    }

    private long occ(String source, String rec, String from, String precision, String method, String level,
                     Double lat, Double lon) {
        return jdbc.queryForObject("""
                INSERT INTO flood_occurrence (ckan_resource_id, ckan_record_id, raw_hash, run_id, occurred_from,
                    occurred_to, temporal_precision, geocoded_lat, geocoded_lon, geocode_method, geocode_level, source)
                VALUES ('TESTE-V23', ?, repeat('a', 64), ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, rec, run, Timestamp.from(Instant.parse(from)),
                Timestamp.from(Instant.parse(from).plusSeconds(3600)), precision, lat, lon, method, level, source);
    }

    private String col(long id, String c) {
        return jdbc.queryForObject("SELECT " + c + " FROM flood_occurrence WHERE id = ?", String.class, id);
    }

    @Test
    void confiancaEPrecisaoSaemDaRegraFixa() {
        long ckanRua = occ("CKAN_RECIFE", "1", "2022-05-28T03:00:00Z", "DAY", "GEOCODING_SERVICE", "STREET", -8.05, -34.9);
        long laiCoord = occ("DEFESA_CIVIL_LAI", "2", "2022-05-28T14:30:00Z", "MINUTE", "SOURCE_COORDINATE", null, -8.0501, -34.9001);
        long bairro = occ("CKAN_RECIFE", "3", "2022-05-28T03:00:00Z", "DAY", "GEOCODING_SERVICE", "NEIGHBORHOOD", -8.06, -34.91);
        long imprensa = occ("IMPRENSA", "4", "2022-05-28T15:00:00Z", "HOUR", "GEOCODING_SERVICE", "STREET", -8.05, -34.9);
        long legado = occ("CKAN_RECIFE", "5", "2022-05-28T03:00:00Z", "DAY", "GEOCODING_SERVICE", null, -8.07, -34.92);

        assertThat(col(ckanRua, "spatial_precision")).isEqualTo("LOGRADOURO");
        assertThat(col(ckanRua, "confidence")).isEqualTo("MEDIA");
        assertThat(col(laiCoord, "spatial_precision")).isEqualTo("COORDENADA");
        assertThat(col(laiCoord, "confidence")).isEqualTo("ALTA");
        assertThat(col(bairro, "confidence")).isEqualTo("BAIXA");
        assertThat(col(imprensa, "confidence")).isEqualTo("BAIXA");
        assertThat(col(legado, "spatial_precision")).isNull();
        assertThat(col(legado, "confidence")).isEqualTo("BAIXA");
        assertThat(col(ckanRua, "origin_id")).isEqualTo("CKAN_RECIFE:TESTE-V23:1");
        assertThat(col(ckanRua, "event_type")).isEqualTo("ALAGAMENTO");
    }

    @Test
    void gruposEInventarioSoParaAdmin() throws Exception {
        occ("CKAN_RECIFE", "1", "2022-05-28T03:00:00Z", "DAY", "GEOCODING_SERVICE", "STREET", -8.05, -34.9);
        occ("DEFESA_CIVIL_LAI", "2", "2022-05-28T14:30:00Z", "MINUTE", "SOURCE_COORDINATE", null, -8.0501, -34.9001);

        mockMvc.perform(as(post("/api/admin/ingestion/occurrences/clusters"), userToken()))
                .andExpect(status().isForbidden());
        String body = mockMvc.perform(as(post("/api/admin/ingestion/occurrences/clusters"), adminToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode inv = objectMapper.readTree(body);
        assertThat(inv.get("clusterRuleVersion").asText()).isEqualTo("cluster-v1");
        assertThat(inv.get("bySource").get("DEFESA_CIVIL_LAI").asInt()).isEqualTo(1);
        assertThat(inv.get("circularityWarning").asText()).contains("limiar");

        // os dois registros (fontes diferentes, mesmo dia, 15 m) viram um grupo com hora e confiança ALTA
        var g = jdbc.queryForMap("""
                SELECT c.members, c.temporal_precision, c.confidence, c.sources::text AS sources
                FROM occurrence_cluster c JOIN occurrence_cluster_member m ON m.cluster_id = c.id
                JOIN flood_occurrence o ON o.id = m.occurrence_id
                WHERE o.ckan_resource_id = 'TESTE-V23' LIMIT 1""");
        assertThat(g.get("members")).isEqualTo(2);
        assertThat(g.get("temporal_precision")).isEqualTo("MINUTE");
        assertThat(g.get("confidence")).isEqualTo("ALTA");
        assertThat((String) g.get("sources")).contains("CKAN_RECIFE").contains("DEFESA_CIVIL_LAI");
    }
}
