package com.projeto.mapi.support;

import com.projeto.mapi.service.ingestion.IngestionKind;
import com.projeto.mapi.service.ingestion.IngestionMode;
import com.projeto.mapi.service.ingestion.IngestionPipelineService;
import com.projeto.mapi.service.ingestion.IngestionRequest;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.adapter.OpenMeteoArchiveClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

/** Base dos testes do pipeline: limpa as tabelas do pipeline e traz a fonte falsa TEST_SRC. */
@Import(TestSourceAdapter.class)
public abstract class AbstractIngestionTest extends AbstractTimescaleIntegrationTest {

    protected static final Instant WIDE_FROM = Instant.parse("2026-01-01T00:00:00Z");
    protected static final Instant WIDE_TO = Instant.parse("2027-01-01T00:00:00Z");

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected IngestionPipelineService pipeline;
    @Autowired protected TestSourceAdapter adapter;

    @MockitoBean protected OpenMeteoArchiveClient openMeteo;
    @MockitoBean protected com.projeto.mapi.service.ingestion.adapter.Era5LandClient era5Land;
    // no contexto compartilhado (um contexto a mais estoura max_connections do contêiner de teste)
    @MockitoBean protected com.projeto.mapi.service.ingestion.adapter.AnaLegacyClient anaLegacy;

    @BeforeEach
    void cleanPipeline() {
        jdbc.execute("TRUNCATE observation_revision, observation, ingestion_quarantine, raw_observation, "
                + "ingestion_run, station CASCADE");
        jdbc.update("""
                INSERT INTO source_registry (source, source_tz, verification_state, note)
                VALUES ('TEST_SRC', 'UTC', 'DECLARED', 'teste')
                ON CONFLICT (source) DO UPDATE SET source_tz = 'UTC', verification_state = 'DECLARED'
                """);
        adapter.reset();
    }

    protected void setTz(String source, String tz, String state) {
        jdbc.update("UPDATE source_registry SET source_tz = ?, verification_state = ? WHERE source = ?", tz, state, source);
    }

    protected IngestionRunView refetch(String source) {
        return pipeline.execute(new IngestionRequest(source, WIDE_FROM, WIDE_TO, IngestionMode.REFETCH, IngestionKind.BACKFILL));
    }

    protected IngestionRunView revalidate(String source, Instant from, Instant to) {
        return pipeline.execute(new IngestionRequest(source, from, to, IngestionMode.REVALIDATE, IngestionKind.REPROCESS));
    }

    protected long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
