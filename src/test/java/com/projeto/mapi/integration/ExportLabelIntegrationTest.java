package com.projeto.mapi.integration;

import com.projeto.mapi.config.ExportLabelProperties;
import com.projeto.mapi.dto.UnifiedDataDTO;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.export.DataExportService;
import com.projeto.mapi.service.geocoding.GeocodingService;
import com.projeto.mapi.service.ingestion.occurrence.CkanRawRecord;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceIngestionService;
import com.projeto.mapi.service.tide.TideService;
import com.projeto.mapi.support.AbstractIngestionTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/** F2-BACK-13: rótulo do export vem de flood_occurrence + occurrence_point_match (MODELAGEM-IA A.3.1). */
class ExportLabelIntegrationTest extends AbstractIngestionTest {

    private static final LocalDateTime END = LocalDateTime.of(2026, 5, 10, 0, 0);

    @Autowired private DataExportService export;
    @Autowired private FloodPointRepository points;
    @Autowired private ExportLabelProperties labelProps;
    @Autowired private OccurrenceIngestionService ingestion;
    @MockitoBean private TideService tide;
    @MockitoBean private GeocodingService geocoding;

    private long cfgMain;
    private UUID run;

    @BeforeEach
    void seed() {
        when(tide.getTideHeightAt(anyDouble(), anyDouble(), any(LocalDateTime.class))).thenReturn(1.0);
        limparDependentesDePontos();
        jdbc.execute("DELETE FROM occurrence_point_match");
        jdbc.execute("DELETE FROM flood_occurrence");
        jdbc.update("DELETE FROM linkage_config WHERE created_by = 'test-label' AND id NOT IN (SELECT linkage_config_id FROM occurrence_point_match)");
        points.deleteAll();
        // vigente = 500 m (pergunta 25); a V6 só traz 3 km, então o pipeline cria o config de 500 m (aqui, direto)
        jdbc.update("INSERT INTO linkage_config (kind, radius_m, method, created_by) SELECT 'OCCURRENCE_POINT', 500,"
                + " 'NEAREST_WITHIN_RADIUS', 'test-label' WHERE NOT EXISTS (SELECT 1 FROM linkage_config WHERE kind = 'OCCURRENCE_POINT' AND radius_m = 500)");
        cfgMain = jdbc.queryForObject("SELECT min(id) FROM linkage_config WHERE kind = 'OCCURRENCE_POINT' AND radius_m = 500", Long.class);
        run = UUID.randomUUID();
        jdbc.update("INSERT INTO ingestion_run (id, source, kind, mode, code_version, parser_version, rule_version, started_at, status)"
                + " VALUES (?, 'CKAN_RECIFE', 'BACKFILL', 'REFETCH', 't', 't', 't', now(), 'OK')", run);
    }

    private long point(String slug, double lat) {
        return points.save(FloodPoint.builder().slug(slug).name(slug).latitude(lat).longitude(-34.88).build()).getId();
    }

    private long occurrence(String rec, String from, String to, String precision, long pointId, long cfg) {
        long id = jdbc.queryForObject("INSERT INTO flood_occurrence (ckan_resource_id, ckan_record_id, raw_hash, run_id,"
                + " occurred_from, occurred_to, temporal_precision) VALUES ('r', ?, 'h', ?, ?::timestamptz, ?::timestamptz, ?)"
                + " RETURNING id", Long.class, rec, run, from, to, precision);
        jdbc.update("INSERT INTO occurrence_point_match (occurrence_id, flood_point_id, distance_m, radius_m, linkage_config_id,"
                + " rule_version, matched_at) VALUES (?, ?, 10, 3000, ?, 't', now())", id, pointId, cfg);
        return id;
    }

    private UnifiedDataDTO at(List<UnifiedDataDTO> rows, String utc) {
        return rows.stream().filter(r -> r.getTimestampUtc().toString().equals(utc)).findFirst().orElseThrow();
    }

    @Test
    void horaComOcorrenciaHourEmTMaisHRotulaEJanelaPosEventoExclui() {
        long a = point("pa", -8.05);
        long occ = occurrence("1", "2026-05-08T17:30:00Z", "2026-05-08T17:30:00.000001Z", "HOUR", a, cfgMain);
        var rows = export.exportUnifiedData("pa", 3, END);

        assertThat(at(rows, "2026-05-08T11:00:00Z").getLabelByHorizon().get(6)).isZero();
        var t12 = at(rows, "2026-05-08T12:00:00Z");
        assertThat(t12.getLabelByHorizon().get(6)).isEqualTo(1);
        assertThat(t12.getLabelByHorizon().get(3)).isZero();
        assertThat(t12.getOccurrenceIds()).isEqualTo(String.valueOf(occ));
        assertThat(at(rows, "2026-05-08T15:00:00Z").getLabelByHorizon().get(3)).isEqualTo(1);
        assertThat(at(rows, "2026-05-08T14:00:00Z").getLabelByHorizon().get(3)).isZero();
        // dia civil de Recife do instante t: 03:00Z já é o dia 08 local
        assertThat(at(rows, "2026-05-08T02:00:00Z").getLabelDay()).isZero();
        assertThat(at(rows, "2026-05-08T03:00:00Z").getLabelDay()).isEqualTo(1);

        // janela pós-evento G = H: 18:00Z em diante fica fora (vazio), com motivo
        var t18 = at(rows, "2026-05-08T18:00:00Z");
        assertThat(t18.getLabelByHorizon().get(6)).isNull();
        assertThat(t18.getLabelByHorizon().get(3)).isNull();
        assertThat(t18.getLabelExcludedReason()).isEqualTo("POST_EVENT_WINDOW");
        assertThat(at(rows, "2026-05-08T23:00:00Z").getLabelByHorizon().get(6)).isNull();
        assertThat(at(rows, "2026-05-09T00:00:00Z").getLabelByHorizon().get(6)).isZero();
    }

    @Test
    void diaComEventoDayExcluiAsHorasEnuncaViraNegativo() {
        long b = point("pb", -8.06);
        occurrence("2", "2026-05-08T03:00:00Z", "2026-05-09T03:00:00Z", "DAY", b, cfgMain);
        var rows = export.exportUnifiedData("pb", 3, END);

        var meio = at(rows, "2026-05-08T12:00:00Z");
        assertThat(meio.getLabelByHorizon().get(6)).isNull();
        assertThat(meio.getLabelByHorizon().get(3)).isNull();
        assertThat(meio.getLabelExcludedReason()).isEqualTo("DAY_EVENT_SAME_DAY");
        assertThat(meio.getLabelDay()).isEqualTo(1);
        assertThat(at(rows, "2026-05-07T20:00:00Z").getLabelByHorizon().get(6)).isZero();
        assertThat(at(rows, "2026-05-07T21:00:00Z").getLabelByHorizon().get(6)).isNull();
        assertThat(at(rows, "2026-05-09T05:00:00Z").getLabelExcludedReason()).isEqualTo("POST_EVENT_WINDOW");
        assertThat(at(rows, "2026-05-09T09:00:00Z").getLabelByHorizon().get(6)).isZero();
        assertThat(rows).noneMatch(r -> Integer.valueOf(1).equals(r.getLabelByHorizon().get(6)));
    }

    @Test
    void ocorrenciaForaDoRaioDaConfigVigenteNaoRotula() {
        long c = point("pc", -8.07);
        jdbc.update("INSERT INTO linkage_config (kind, radius_m, method, created_by) VALUES "
                + "('OCCURRENCE_POINT', 250, 'NEAREST_WITHIN_RADIUS', 'test-label')");
        long cfgAlt = jdbc.queryForObject("SELECT id FROM linkage_config WHERE created_by = 'test-label' AND radius_m = 250", Long.class);
        occurrence("3", "2026-05-08T10:00:00Z", "2026-05-08T10:00:00.000001Z", "HOUR", c, cfgAlt);

        var rows = export.exportUnifiedData("pc", 3, END);   // vigente = 500 m
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getLabelByHorizon().get(6)).isZero();
            assertThat(r.getLabelDay()).isZero();
            assertThat(r.getOccurrenceIds()).isEmpty();
        });

        labelProps.setLinkageConfigId(cfgAlt);               // sensibilidade: outro raio => outro rótulo
        try {
            var m = export.exportCsv("pc", 3, END).manifest().label();
            assertThat(m.linkageConfigId()).isEqualTo(cfgAlt);
            assertThat(m.linkageRadiusM()).isEqualTo(250.0);
            assertThat(m.counts().get("label_h6").positives()).isEqualTo(6);
        } finally {
            labelProps.setLinkageConfigId(null);
        }
    }

    @Test
    void ingestaoRealCasaPeloRaioEForaDoRaioNaoRotula() {
        long d = point("pd", -8.0500);
        jdbc.update("UPDATE flood_points SET longitude = -34.9000 WHERE id = ?", d);
        when(geocoding.geocode(any(), any(), any())).thenAnswer(inv -> {
            String addr = inv.getArgument(0);
            if (addr.contains("perto")) return Optional.of(new double[]{-8.0473, -34.9000});   // ~300 m
            if (addr.contains("longe")) return Optional.of(new double[]{-8.0410, -34.9000});   // ~1 km: fora de 500 m
            return Optional.empty();
        });
        ingestion.ingest("pkg", "res-x", List.of(
                new CkanRawRecord("1", Map.of("_id", "1", "Ocorrencia", "Alagamentos", "Data", "28/05/2024 14:30:00",
                        "Endereco", "Rua Perto, 10", "Bairro", "Boa Viagem")),
                new CkanRawRecord("2", Map.of("_id", "2", "Ocorrencia", "Alagamentos", "Data", "28/05/2024 14:30:00",
                        "Endereco", "Rua Longe, 10", "Bairro", "Boa Viagem"))));
        long perto = jdbc.queryForObject("SELECT id FROM flood_occurrence WHERE ckan_record_id = '1'", Long.class);

        var rows = export.exportUnifiedData("pd", 2, LocalDateTime.of(2024, 5, 29, 0, 0));
        var t12 = at(rows, "2024-05-28T12:00:00Z");
        assertThat(t12.getLabelByHorizon().get(6)).isEqualTo(1);
        assertThat(t12.getOccurrenceIds()).isEqualTo(String.valueOf(perto));   // a de 1 km não aparece
        assertThat(rows.stream().filter(r -> Integer.valueOf(1).equals(r.getLabelByHorizon().get(6))).count()).isEqualTo(6);
    }

    @Test
    void cabecalhoNaoMisturaRotuloComFeatureEManifestoTemContagens() {
        long a = point("pa", -8.05);
        long b = point("pb", -8.06);
        point("pc", -8.07);
        occurrence("1", "2026-05-08T17:30:00Z", "2026-05-08T17:30:00.000001Z", "HOUR", a, cfgMain);
        occurrence("2", "2026-05-08T03:00:00Z", "2026-05-09T03:00:00Z", "DAY", b, cfgMain);

        var result = export.exportCsv("all", 3, END);
        String header = result.csv().split("\n")[0];
        List<String> cols = List.of(header.split(","));
        int firstLabel = cols.indexOf("label_h6");
        assertThat(cols.subList(firstLabel, firstLabel + 5))
                .containsExactly("label_h6", "label_h3", "label_day", "label_excluded_reason", "occurrence_ids");
        // nenhuma coluna de feature (antes do bloco de rótulo) carrega rótulo/ocorrência; as terrain_* vêm depois
        cols.subList(0, firstLabel).forEach(c -> assertThat(c.toLowerCase()).doesNotContain("label").doesNotContain("occurrence"));
        cols.subList(firstLabel + 5, cols.size()).forEach(c -> assertThat(c).matches("terrain_.*|soil_moisture_0_7cm(_missing)?"));
        // coluna antiga segue no CSV, marcada como obsoleta no manifesto
        assertThat(cols).contains("isFlooded", "severity");
        assertThat(result.manifest().deprecatedColumns()).containsExactly("isFlooded", "severity");

        var m = result.manifest().label();
        assertThat(m.horizonsHours()).containsExactly(6, 3);
        assertThat(m.postEventWindowHours()).containsEntry(6, 6).containsEntry(3, 3);
        assertThat(m.linkageConfigId()).isEqualTo(cfgMain);
        assertThat(m.linkageRadiusM()).isEqualTo(500.0);
        assertThat(m.policy()).contains("flood_occurrence").contains("DAY_EVENT_SAME_DAY");
        assertThat(m.occurrencesHour()).isEqualTo(1);
        assertThat(m.occurrencesDay()).isEqualTo(1);
        var h6 = m.counts().get("label_h6");
        assertThat(h6.positives()).isEqualTo(6);
        assertThat(h6.excluded()).isEqualTo(42);      // 6 pós-evento (pa) + 30 DAY + 6 pós-evento (pb)
        assertThat(h6.positives() + h6.negatives() + h6.excluded()).isEqualTo(3 * 73);
        assertThat(m.counts().get("label_h3").positives()).isEqualTo(3);
        assertThat(m.excludedByReason()).containsEntry("DAY_EVENT_SAME_DAY", 30).containsEntry("POST_EVENT_WINDOW", 12);
        assertThat(m.dayLabelPositives()).isEqualTo(48);   // dia civil 08/05 de Recife (24 h) em pa e em pb
    }
}
