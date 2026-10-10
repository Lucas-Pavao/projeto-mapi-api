package com.projeto.mapi.integration;

import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.geocoding.GeocodingService;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.occurrence.CkanRawRecord;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceIngestionService;
import com.projeto.mapi.support.AbstractIngestionTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** T7 do ADR 0006: rótulo CKAN sem realinhamento, filtro de texto e raio gravado. */
class OccurrenceIngestionIntegrationTest extends AbstractIngestionTest {

    private static final String RES = "res-1";

    @Autowired private OccurrenceIngestionService service;
    @Autowired private FloodPointRepository points;
    @MockitoBean private GeocodingService geocoding;

    @BeforeEach
    void setup() {
        jdbc.execute("TRUNCATE flood_point_pluviometers, flood_point_river_levels, flood_point_weather_stations, "
                + "flood_events, flood_points CASCADE");
        jdbc.update("DELETE FROM linkage_config WHERE created_by = 'pipeline'");
        points.save(FloodPoint.builder().slug("p1").name("P1").latitude(-8.0500).longitude(-34.9000).build());
        // o sanitizador legado ("rua perto, 10" -> "rua perto, , 10") é preservado; casa pelo trecho do nome
        when(geocoding.geocode(any(), any(), any())).thenAnswer(inv -> {
            String addr = inv.getArgument(0);
            if (addr.contains("perto")) return Optional.of(new double[]{-8.0410, -34.9000}); // ~1 km
            if (addr.contains("longe")) return Optional.of(new double[]{-8.0950, -34.9000}); // ~5 km
            return Optional.empty();
        });
    }

    private static CkanRawRecord rec(String id, String tipo, String data, String end) {
        return new CkanRawRecord(id, Map.of("_id", id, "Ocorrencia", tipo, "Data", data, "Endereco", end, "Bairro", "Boa Viagem"));
    }

    private IngestionRunView load() {
        return service.ingest("pkg", RES, List.of(
                rec("1", "Alagamentos", "2024-05-28T00:00:00", "Rua Perto, 10"),
                rec("2", "Alagamentos", "28/05/2024 14:30:00", "Rua Longe, 10"),
                rec("3", "Deslizamento de barreira", "2024-05-28T00:00:00", "Rua Perto, 10"),
                rec("4", "Enxurrada", "2024-05-28T00:00:00", "Rua Perto, 10"),
                rec("5", "Inundação", "2024-05-28T00:00:00", "Rua Perto, 10"),
                rec("6", "Alagamentos", "sem data", "Rua Perto, 10")));
    }

    @Test
    void t7_diaFicaDiaFiltroTextoERaioGravado() {
        IngestionRunView run = load();
        assertThat(run.status()).isEqualTo("OK");
        assertThat(run.source()).isEqualTo("CKAN_RECIFE");
        assertThat(run.fetched()).isEqualTo(6);
        assertThat(run.rawInserted()).isEqualTo(6);       // tudo vai para o bruto, inclusive o excluído
        assertThat(run.valid()).isEqualTo(2);
        assertThat(run.quarantined()).isEqualTo(1);       // registro 6: data ilegível

        // só "alagamento" vira ocorrência
        assertThat(count("SELECT count(*) FROM flood_occurrence")).isEqualTo(2);
        // DAY nunca vira hora: intervalo original do dia local (Recife = UTC-3)
        assertThat(jdbc.queryForObject("SELECT temporal_precision FROM flood_occurrence WHERE ckan_record_id = '1'", String.class)).isEqualTo("DAY");
        assertThat(count("SELECT count(*) FROM flood_occurrence WHERE ckan_record_id = '1' "
                + "AND occurred_from = '2024-05-28T03:00:00Z' AND occurred_to = '2024-05-29T03:00:00Z'")).isEqualTo(1);
        // com hora: instante
        assertThat(count("SELECT count(*) FROM flood_occurrence WHERE ckan_record_id = '2' AND temporal_precision = 'HOUR' "
                + "AND occurred_from = '2024-05-28T17:30:00Z'")).isEqualTo(1);
        // nenhuma consulta de chuva: a ingestão de rótulo não toca observation
        assertThat(count("SELECT count(*) FROM observation")).isZero();

        // raio e config gravados; só a ocorrência 1 (1 km) está dentro de 3 km
        assertThat(count("SELECT count(*) FROM occurrence_point_match")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM occurrence_point_match m JOIN linkage_config c ON c.id = m.linkage_config_id "
                + "WHERE m.radius_m = 3000 AND c.radius_m = 3000 AND m.distance_m BETWEEN 900 AND 1100 "
                + "AND m.rule_version IS NOT NULL")).isEqualTo(1);
    }

    @Test
    void t7_trocarORaioGeraNovasLinhasSemApagarAsAntigas() {
        load();
        long cfg6 = service.rematch(6000);
        assertThat(cfg6).isNotEqualTo(jdbc.queryForObject("SELECT min(id) FROM linkage_config WHERE kind = 'OCCURRENCE_POINT'", Long.class));
        // raio 6 km casa as duas ocorrências; as linhas do raio de 3 km continuam
        assertThat(count("SELECT count(*) FROM occurrence_point_match WHERE radius_m = 3000")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM occurrence_point_match WHERE radius_m = 6000")).isEqualTo(2);
        // repetir o mesmo raio não duplica
        service.rematch(6000);
        assertThat(count("SELECT count(*) FROM occurrence_point_match")).isEqualTo(3);
    }

    @Test
    void reimportarIgualNaoMudaRevisaoEMudancaIncrementa() {
        load();
        IngestionRunView again = load();
        assertThat(again.rawInserted()).isZero();
        assertThat(again.rawUnchanged()).isEqualTo(6);
        assertThat(count("SELECT count(*) FROM flood_occurrence WHERE revision = 1")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM occurrence_point_match")).isEqualTo(1);

        // a fonte corrigiu o endereço: o bruto ganha uma versão nova e a revisão sobe
        service.ingest("pkg", RES, List.of(rec("1", "Alagamentos", "2024-05-28T00:00:00", "Rua Longe, 10")));
        assertThat(count("SELECT count(*) FROM raw_observation WHERE station_id = 'res-1#1'")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM flood_occurrence WHERE ckan_record_id = '1' AND revision = 2")).isEqualTo(1);
    }
}
