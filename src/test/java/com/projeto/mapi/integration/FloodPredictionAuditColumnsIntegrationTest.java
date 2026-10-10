package com.projeto.mapi.integration;

import com.projeto.mapi.dto.DegradationReason;
import com.projeto.mapi.dto.FloodPredictionRequestDTO;
import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.dto.RiskLevel;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.FloodPrediction;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.FloodPredictionRepository;
import com.projeto.mapi.service.flood.FeatureCatalog;
import com.projeto.mapi.service.flood.FloodPredictionHistoryService;
import com.projeto.mapi.service.flood.PredictionResult;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Migração V8: colunas de auditoria de flood_predictions num TimescaleDB real (ddl-auto=validate). */
class FloodPredictionAuditColumnsIntegrationTest extends AbstractTimescaleIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private FloodPredictionRepository repository;
    @Autowired private FloodPointRepository pointRepository;
    @Autowired private FloodEventRepository eventRepository;
    @Autowired private FloodPredictionHistoryService history;
    @Autowired private FeatureCatalog catalog;

    private FloodPoint ponto;

    @BeforeEach
    void limpa() {
        jdbc.execute("DELETE FROM flood_predictions");
        eventRepository.deleteAll();
        pointRepository.deleteAll();
        ponto = pointRepository.save(FloodPoint.builder().slug("p-v8").name("Ponto V8")
                .latitude(-8.05).longitude(-34.88).build());
    }

    private FloodPredictionRequestDTO req() {
        return FloodPredictionRequestDTO.builder().stationId("S1").latitude(-8.05).longitude(-34.88)
                .currentRainfall(1.0).rainfall3hAccumulated(2.0).rainfall6hAccumulated(3.0)
                .rainfall12hAccumulated(4.0).rainfall24hAccumulated(5.0).tideLevel(1.1).riverLevel(0.5).build();
    }

    private PredictionResult sucesso(Instant at) {
        return new PredictionResult(FloodPredictionResponseDTO.builder()
                .riskLevel(RiskLevel.MEDIUM).floodProbability(0.42).modelVersion("xgb-h3-2026.11.0")
                .issuedAt(at).horizonHours(3).validUntil(at.plus(3, ChronoUnit.HOURS))
                .contributors(List.of(catalog.contributor("tide_height_t1", 0.18)))
                .imputedFeatures(catalog.imputed(List.of("river_level"))).build(), "xgb-h3-2026.11.0+a1b2c3d");
    }

    private PredictionResult falha(Instant at, DegradationReason r) {
        return new PredictionResult(FloodPredictionResponseDTO.builder().riskLevel(RiskLevel.UNKNOWN)
                .issuedAt(at).degradationReason(r).message(r.message()).build(), null);
    }

    @Test
    void migracaoAplicaEColunasExistem() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).contains("8");
        List<String> colunas = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'flood_predictions'", String.class);
        assertThat(colunas).contains("model_version", "model_version_full", "horizon_hours", "degradation_reason",
                "imputed_features", "top_contributors", "flood_point_id", "issued_at");
        assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns "
                + "WHERE table_name='flood_predictions' AND column_name='issued_at'", String.class))
                .isEqualTo("timestamp with time zone");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint "
                + "WHERE conname = 'fk_flood_predictions_flood_point' AND conrelid = 'flood_predictions'::regclass", Integer.class)).isEqualTo(1);
    }

    @Test
    void checkRejeitaMotivoForaDoEnumEAceitaNulo() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO flood_predictions (timestamp, degradation_reason) "
                + "VALUES (now(), 'INVENTADO')")).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO flood_predictions (timestamp, degradation_reason) VALUES (now(), NULL)");
        jdbc.update("INSERT INTO flood_predictions (timestamp, degradation_reason) VALUES (now(), 'AI_TIMEOUT')");
    }

    @Test
    void fkRejeitaPontoInexistente() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO flood_predictions (timestamp, flood_point_id) VALUES (now(), 999999)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void linhaAntigaFicaComColunasNovasNulasEImputedPadrao() {
        jdbc.update("INSERT INTO flood_predictions (timestamp, status, message) VALUES (now(), 'SUCCESS', 'x')");
        Map<String, Object> r = jdbc.queryForMap("SELECT issued_at, flood_point_id, model_version, "
                + "imputed_features::text AS imp FROM flood_predictions");
        assertThat(r.get("issued_at")).isNull();
        assertThat(r.get("flood_point_id")).isNull();
        assertThat(r.get("model_version")).isNull();
        assertThat(r.get("imp")).isEqualTo("[]");
    }

    @Test
    void gravacaoPreencheColunasEMessageSemJson() {
        Instant t = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        history.record(req(), sucesso(t), ponto.getId());
        history.record(req(), falha(t.plusSeconds(60), DegradationReason.AI_TIMEOUT), ponto.getId());

        List<FloodPrediction> rows = repository.findAll();
        assertThat(rows).hasSize(2);
        FloodPrediction ok = rows.stream().filter(r -> "SUCCESS".equals(r.getStatus())).findFirst().orElseThrow();
        assertThat(ok.getFloodPointId()).isEqualTo(ponto.getId());
        assertThat(ok.getModelVersion()).isEqualTo("xgb-h3-2026.11.0");
        assertThat(ok.getModelVersionFull()).isEqualTo("xgb-h3-2026.11.0+a1b2c3d");
        assertThat(ok.getHorizonHours()).isEqualTo(3);
        assertThat(ok.getDegradationReason()).isNull();
        assertThat(ok.getImputedFeatures()).containsExactly("river_level");
        assertThat(ok.getTopContributors()).extracting("featureId").containsExactly("tide_height_t1");
        assertThat(ok.getIssuedAt()).isEqualTo(t);

        FloodPrediction ko = rows.stream().filter(r -> "FAILED".equals(r.getStatus())).findFirst().orElseThrow();
        assertThat(ko.getDegradationReason()).isEqualTo("AI_TIMEOUT");
        assertThat(ko.getFloodProbability()).isNull();
        assertThat(ko.getMessage()).isEqualTo(DegradationReason.AI_TIMEOUT.message());

        for (FloodPrediction r : rows) {
            assertThat(r.getMessage()).doesNotContain("{").doesNotContain("[");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flood_predictions WHERE message LIKE '%{%'",
                Integer.class)).isZero();
    }

    @Test
    void leituraDevolveAuditoriaDasColunas() {
        Instant t = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        history.record(req(), sucesso(t), ponto.getId());
        FloodPredictionResponseDTO lida = history.findLatest(ponto.getId(), -8.05, -34.88).orElseThrow();
        assertThat(lida.modelVersion()).isEqualTo("xgb-h3-2026.11.0");
        assertThat(lida.horizonHours()).isEqualTo(3);
        assertThat(lida.issuedAt()).isEqualTo(t);
        assertThat(lida.contributors()).hasSize(1);
        assertThat(lida.imputedFeatures()).extracting("featureId").containsExactly("river_level");
    }

    @Test
    void lastValidIssuedAtPorPontoIgnoraOutroPontoNaMesmaCoordenada() {
        FloodPoint outro = pointRepository.save(FloodPoint.builder().slug("p-v8-b").name("B")
                .latitude(-8.05).longitude(-34.88).build());
        Instant t1 = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(30, ChronoUnit.MINUTES);
        history.record(req(), sucesso(t1), ponto.getId());
        history.record(req(), sucesso(t1.plus(10, ChronoUnit.MINUTES)), outro.getId());
        history.record(req(), falha(t1.plus(20, ChronoUnit.MINUTES), DegradationReason.AI_ERROR), ponto.getId());

        assertThat(history.findLastValidIssuedAt(ponto.getId(), -8.05, -34.88)).contains(t1);
        assertThat(history.findLastValidIssuedAt(outro.getId(), -8.05, -34.88)).contains(t1.plus(10, ChronoUnit.MINUTES));
    }

    @Test
    void semLinhaComPontoUsaABuscaAntigaPorCoordenada() {
        Instant t = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        history.record(req(), sucesso(t), null); // POST de cenário / linha anterior à V8: sem ponto
        assertThat(repository.findAll().get(0).getFloodPointId()).isNull();
        assertThat(history.findLastValidIssuedAt(ponto.getId(), -8.05, -34.88)).contains(t);
        assertThat(history.findLatest(ponto.getId(), -8.05, -34.88)).isPresent();
    }

    @Test
    void consultaPorPontoUsaOIndice() {
        jdbc.execute("INSERT INTO flood_points (name, slug, latitude, longitude) "
                + "SELECT 'P'||g, 'idx-'||g, -8, -34 FROM generate_series(1, 50) g");
        jdbc.execute("INSERT INTO flood_predictions (timestamp, flood_point_id, status, risk_level) "
                + "SELECT now() - (g || ' minutes')::interval - interval '1 minute', "
                + "(SELECT min(id) FROM flood_points WHERE slug LIKE 'idx-%') + (g % 50), 'SUCCESS', 'LOW' "
                + "FROM generate_series(1, 30000) g");
        jdbc.execute("ANALYZE flood_predictions");
        Long id = jdbc.queryForObject("SELECT min(id) FROM flood_points WHERE slug LIKE 'idx-%'", Long.class);

        List<String> plano = jdbc.queryForList("EXPLAIN (ANALYZE, COSTS OFF, TIMING OFF, SUMMARY OFF) SELECT * FROM flood_predictions WHERE flood_point_id = "
                + id + " AND status = 'SUCCESS' ORDER BY timestamp DESC LIMIT 1", String.class);
        System.out.println("EXPLAIN_V8:\n" + String.join("\n", plano));
        assertThat(String.join("\n", plano)).contains("idx_prediction_point_timestamp").contains("Index");
    }
}
