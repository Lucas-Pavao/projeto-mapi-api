package com.projeto.mapi.integration;

import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.support.AbstractIngestionTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.projeto.mapi.support.TestSourceAdapter.r;
import static com.projeto.mapi.support.TestSourceAdapter.rec;
import static org.assertj.core.api.Assertions.assertThat;

/** Testes T1, T2a, T2c, T2d, T2e, T4, T5, T5b e T6 do ADR 0006 sobre um TimescaleDB real. */
class IngestionPipelineIntegrationTest extends AbstractIngestionTest {

    private static final String S = "TEST_SRC";

    private List<RawRecord> day(double value) {
        List<RawRecord> list = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            list.add(rec("S1", String.format("2026-10-01T%02d:00", h), r("precipitation", "PT1H", value)));
        }
        return list;
    }

    private String tableHash() {
        return jdbc.queryForObject("""
                SELECT md5(coalesce(string_agg(station_id || variable || ts_utc || value || revision || status,
                  ',' ORDER BY station_id, variable, ts_utc), '')) FROM observation
                """, String.class);
    }

    @Test
    void t1_reexecutarMesmoIntervaloNaoAlteraNada() {
        adapter.setBatches(day(1.5));
        IngestionRunView first = refetch(S);
        assertThat(first.status()).isEqualTo("OK");
        assertThat(first.rawInserted()).isEqualTo(24);
        assertThat(first.obsInserted()).isEqualTo(24);
        String hash = tableHash();

        adapter.setBatches(day(1.5));
        IngestionRunView second = refetch(S);
        assertThat(second.rawInserted()).isZero();
        assertThat(second.rawUnchanged()).isEqualTo(24);
        assertThat(second.obsInserted()).isZero();
        assertThat(second.obsUpdated()).isZero();
        assertThat(second.obsUnchanged()).isEqualTo(24);
        assertThat(second.absentInRefetch()).isZero();
        assertThat(tableHash()).isEqualTo(hash);
        assertThat(count("SELECT count(*) FROM raw_observation")).isEqualTo(24);
        assertThat(count("SELECT count(*) FROM ingestion_run")).isEqualTo(2);
        assertThat(second.parentRunId()).isEqualTo(first.id());
        assertThat(count("SELECT count(*) FROM observation_revision")).isZero();
    }

    @Test
    void t1_concorrenciaNaMesmaFonteNaoDuplica() throws Exception {
        adapter.setBatches(day(2.0));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<IngestionRunView>> tasks = List.of(() -> refetch(S), () -> refetch(S));
        List<Future<IngestionRunView>> results = pool.invokeAll(tasks);
        for (Future<IngestionRunView> f : results) assertThat(f.get().status()).isEqualTo("OK");
        pool.shutdown();
        assertThat(count("SELECT count(*) FROM raw_observation")).isEqualTo(24);
        assertThat(count("SELECT count(*) FROM observation")).isEqualTo(24);
        assertThat(count("SELECT count(*) FROM observation WHERE revision <> 1")).isZero();
    }

    @Test
    void t2a_correcaoRetroativaGeraRevisaoETrilha() {
        adapter.setBatches(day(1.0));
        IngestionRunView v1 = refetch(S);
        List<RawRecord> corrected = day(1.0);
        corrected.set(5, rec("S1", "2026-10-01T05:00", r("precipitation", "PT1H", 9.0)));
        adapter.setBatches(corrected);
        IngestionRunView v2 = refetch(S);

        assertThat(v2.obsUpdated()).isEqualTo(1);
        assertThat(v2.rawInserted()).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("SELECT value, revision, run_id FROM observation "
                + "WHERE ts_utc = '2026-10-01T05:00:00Z'");
        assertThat(row.get("value")).isEqualTo(9.0);
        assertThat(row.get("revision")).isEqualTo(2);
        assertThat(row.get("run_id").toString()).isEqualTo(v2.id().toString());
        Map<String, Object> old = jdbc.queryForMap("SELECT value, run_id, superseded_by_run_id FROM observation_revision");
        assertThat(old.get("value")).isEqualTo(1.0);
        assertThat(old.get("run_id").toString()).isEqualTo(v1.id().toString());
        assertThat(old.get("superseded_by_run_id").toString()).isEqualTo(v2.id().toString());
        assertThat(count("SELECT count(*) FROM observation WHERE revision = 1")).isEqualTo(23);
        assertThat(count("SELECT count(*) FROM raw_observation")).isEqualTo(25);
    }

    @Test
    void t2c_horaRefleteCorrecaoSemRefresh() {
        adapter.setBatches(List.of(rec("S1", "2026-10-01T05:00", r("precipitation", "PT1H", 1.0))));
        refetch(S);
        adapter.setBatches(List.of(rec("S1", "2026-10-01T05:00", r("precipitation", "PT1H", 4.0))));
        refetch(S);
        Double v = jdbc.queryForObject("SELECT value FROM observation_hourly_v WHERE station_id = 'S1' "
                + "AND hour_end_utc = '2026-10-01T05:00:00Z'", Double.class);
        assertThat(v).isEqualTo(4.0);
    }

    @Test
    void t2d_voltaAoValorOriginalEhRegistrada() {
        adapter.setBatches(List.of(rec("S1", "2026-10-01T05:00", r("precipitation", "PT1H", 1.0))));
        refetch(S);
        adapter.setBatches(List.of(rec("S1", "2026-10-01T05:00", r("precipitation", "PT1H", 1.0))));
        IngestionRunView same = refetch(S); // v1, v1: continua raw_unchanged
        assertThat(same.rawUnchanged()).isEqualTo(1);
        assertThat(same.rawInserted()).isZero();
        adapter.setBatches(List.of(rec("S1", "2026-10-01T05:00", r("precipitation", "PT1H", 2.0))));
        refetch(S);
        adapter.setBatches(List.of(rec("S1", "2026-10-01T05:00", r("precipitation", "PT1H", 1.0))));
        IngestionRunView back = refetch(S);

        assertThat(back.rawInserted()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM raw_observation")).isEqualTo(3);
        Map<String, Object> o = jdbc.queryForMap("SELECT value, revision FROM observation");
        assertThat(o.get("value")).isEqualTo(1.0);
        assertThat(o.get("revision")).isEqualTo(3);
        List<Double> trilha = jdbc.queryForList("SELECT value FROM observation_revision ORDER BY revision", Double.class);
        assertThat(trilha).containsExactly(1.0, 2.0);
    }

    @Test
    void t2e_loteComVersoesRepetidasDoMesmoRegistroVenceAMaisRecente() {
        List<RawRecord> batch = List.of(
                rec("S1", "2026-10-01T05:00", r("precipitation", "PT1H", 1.0)),
                rec("S1", "2026-10-01T05:00", r("precipitation", "PT1H", 7.0)));
        adapter.setBatches(batch);
        IngestionRunView run = refetch(S);
        assertThat(run.status()).isEqualTo("OK");
        assertThat(run.batchDeduped()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT value FROM observation", Double.class)).isEqualTo(7.0);
        assertThat(count("SELECT count(*) FROM raw_observation")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM ingestion_quarantine")).isZero();
    }

    @Test
    void t4_quarentenaComMotivoELacunaSemConstante() {
        adapter.setBatches(List.of(
                rec("S1", "2026-10-01T01:00", r("precipitation", "PT1H", -9999.0)),
                rec("S1", "2026-10-01T02:00", r("precipitation", "PT1H", 5000.0)),
                new_unit("S1", "2026-10-01T03:00"),
                rec("S1", "2026-10-01T04:00", r("precipitation", "PT1H", 2.0)),
                rec("S1", "2026-10-01T06:00", r("precipitation", "PT1H", 3.0)), // lacuna: falta 05:00
                rec("S2", "2026-10-02", r("precipitation", "PT1H", 1.0)),
                rec("S2", "2026-10-02T00:00", r("precipitation", "PT1H", 2.0)))); // mesma chave natural, valor diferente
        IngestionRunView run = refetch(S);

        List<String> motivos = jdbc.queryForList("SELECT reason FROM ingestion_quarantine ORDER BY reason", String.class);
        assertThat(motivos).containsExactlyInAnyOrder("SENTINEL", "OUT_OF_RANGE", "UNIT_MISMATCH",
                "CONFLICT_IN_BATCH", "CONFLICT_IN_BATCH");
        assertThat(count("SELECT count(*) FROM ingestion_quarantine WHERE rule_id IS NULL OR rule_version IS NULL")).isZero();
        assertThat(count("SELECT count(*) FROM observation WHERE station_id = 'S2'")).isZero();
        assertThat(count("SELECT count(*) FROM observation WHERE station_id = 'S1'")).isEqualTo(2);
        assertThat(run.gaps()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quality_flag FROM observation WHERE ts_utc = '2026-10-01T06:00:00Z'",
                String.class)).isEqualTo("GAP_BEFORE");
        assertThat(count("SELECT count(*) FROM observation WHERE ts_utc = '2026-10-01T05:00:00Z'")).isZero();
    }

    private RawRecord new_unit(String station, String ts) {
        return rec(station, ts, new com.projeto.mapi.support.TestSourceAdapter.Reading("precipitation", "PT1H", 1.0, "furlongs"));
    }

    @Test
    void t3_fonteNaoVerificadaVaiParaQuarentenaEReavaliaDepois() {
        setTz(S, "UNVERIFIED", "UNVERIFIED");
        adapter.setBatches(List.of(rec("S1", "2026-10-01T05:00", r("precipitation", "PT1H", 1.0))));
        IngestionRunView run = refetch(S);
        assertThat(run.quarantined()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM observation")).isZero();
        assertThat(jdbc.queryForObject("SELECT reason FROM ingestion_quarantine", String.class)).isEqualTo("TZ_UNVERIFIED");

        // T5: REVALIDATE com a regra de fuso corrigida reclassifica a quarentena
        setTz(S, "UTC", "VERIFIED");
        IngestionRunView re = revalidate(S, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-02T00:00:00Z"));
        assertThat(re.status()).isEqualTo("OK");
        assertThat(re.rawInserted()).isZero();
        assertThat(count("SELECT count(*) FROM observation")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM ingestion_quarantine WHERE resolved_at IS NOT NULL")).isEqualTo(1);
    }

    @Test
    void t5_revalidateSoLeEEscreveRegistrosDeOrigemDoIntervalo() {
        List<RawRecord> batch = new ArrayList<>(day(1.0));
        for (int h = 0; h < 24; h++) {
            batch.add(rec("S1", String.format("2026-10-05T%02d:00", h), r("precipitation", "PT1H", 3.0)));
        }
        adapter.setBatches(batch);
        refetch(S);
        String outsideBefore = jdbc.queryForObject("""
                SELECT md5(string_agg(ts_utc || ':' || value || ':' || revision || ':' || run_id, ',' ORDER BY ts_utc))
                FROM observation WHERE ts_utc >= '2026-10-05T00:00:00Z'""", String.class);
        IngestionRunView re = revalidate(S, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-02T00:00:00Z"));
        assertThat(re.fetched()).isEqualTo(24);
        assertThat(re.obsInserted()).isZero();
        assertThat(re.obsUpdated()).isZero();
        String outsideAfter = jdbc.queryForObject("""
                SELECT md5(string_agg(ts_utc || ':' || value || ':' || revision || ':' || run_id, ',' ORDER BY ts_utc))
                FROM observation WHERE ts_utc >= '2026-10-05T00:00:00Z'""", String.class);
        assertThat(outsideAfter).isEqualTo(outsideBefore);
    }

    @Test
    void t5b_revalidacaoComDeslocamentoDeFusoNaoPerdeDado() {
        adapter.setBatches(List.of(
                rec("S1", "2026-10-01T10:00", r("precipitation", "PT1H", 1.0)),
                rec("S1", "2026-10-01T11:00", r("precipitation", "PT1H", 2.0)),
                rec("S1", "2026-10-01T12:00", r("precipitation", "PT1H", 3.0))));
        refetch(S);
        Instant from = Instant.parse("2026-10-01T10:00:00Z");
        Instant to = Instant.parse("2026-10-01T12:00:00Z");

        setTz(S, "America/Recife", "VERIFIED"); // 10:00 local = 13:00Z
        IngestionRunView re = revalidate(S, from, to);
        assertThat(re.obsRetracted()).isEqualTo(2);
        assertThat(statusAt("2026-10-01T10:00:00Z")).isEqualTo("RETRACTED");
        assertThat(statusAt("2026-10-01T11:00:00Z")).isEqualTo("RETRACTED");
        assertThat(statusAt("2026-10-01T12:00:00Z")).isEqualTo("ACTIVE"); // origem fora da janela: intocada
        assertThat(statusAt("2026-10-01T13:00:00Z")).isEqualTo("ACTIVE"); // destino fora da janela, mas gravado
        assertThat(statusAt("2026-10-01T14:00:00Z")).isEqualTo("ACTIVE");
        assertThat(count("SELECT count(*) FROM raw_observation")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM observation")).isEqualTo(5);

        setTz(S, "UTC", "VERIFIED"); // reverte a regra: reativa as originais com o mesmo valor
        revalidate(S, from, to);
        assertThat(statusAt("2026-10-01T10:00:00Z")).isEqualTo("ACTIVE");
        assertThat(statusAt("2026-10-01T11:00:00Z")).isEqualTo("ACTIVE");
        assertThat(statusAt("2026-10-01T13:00:00Z")).isEqualTo("RETRACTED");
        assertThat(statusAt("2026-10-01T14:00:00Z")).isEqualTo("RETRACTED");
        assertThat(jdbc.queryForObject("SELECT value FROM observation WHERE ts_utc = '2026-10-01T10:00:00Z'",
                Double.class)).isEqualTo(1.0);
        assertThat(count("SELECT count(*) FROM observation_revision WHERE reason = 'REACTIVATED'")).isEqualTo(2);
    }

    private String statusAt(String ts) {
        return jdbc.queryForObject("SELECT status FROM observation WHERE ts_utc = ?::timestamptz", String.class, ts);
    }

    @Test
    void t6_horaSomaUltimoEConvencaoDeFimDeIntervalo() {
        adapter.setBatches(List.of(
                rec("S1", "2026-10-01T14:05", r("precipitation", "PT15M", 1.0), r("water_level", "PT15M", 10.0)),
                rec("S1", "2026-10-01T14:20", r("precipitation", "PT15M", 2.0), r("water_level", "PT15M", 11.0)),
                rec("S1", "2026-10-01T14:50", r("precipitation", "PT15M", 4.0), r("water_level", "PT15M", 12.0)),
                rec("S1", "2026-10-01T15:00:00", r("precipitation", "PT15M", 8.0), r("water_level", "PT15M", 13.0)),
                rec("S1", "2026-10-01T15:01", r("precipitation", "PT15M", 16.0), r("water_level", "PT15M", 14.0)),
                rec("S1", "2026-10-01", r("precipitation_daily_total", "P1D", 40.0))));
        refetch(S);

        Map<String, Object> rain = hourly("precipitation", "2026-10-01T15:00:00Z");
        assertThat(rain.get("value")).isEqualTo(15.0); // 1+2+4+8: a leitura de 15:00:00 pertence a (14:00, 15:00]
        assertThat(rain.get("n_readings")).isEqualTo(4);
        assertThat(hourly("water_level", "2026-10-01T15:00:00Z").get("value")).isEqualTo(13.0); // última
        Map<String, Object> next = hourly("precipitation", "2026-10-01T16:00:00Z");
        assertThat(next.get("n_readings")).isEqualTo(1); // a de 15:01
        assertThat(next.get("value")).isNull(); // 1 de 4 < 80%: NULL, nunca constante
        assertThat(count("SELECT count(*) FROM observation_hourly_v WHERE variable = 'precipitation_daily_total'")).isZero();
        assertThat(count("SELECT count(*) FROM observation WHERE variable = 'precipitation_daily_total'")).isEqualTo(1);
        // P1D: dia civil local de Recife = [03:00Z, 03:00Z do dia seguinte); só o fim fica disponível (fuso da fonte
        // de teste é UTC, então o fim é 00:00Z do dia 02)
        assertThat(jdbc.queryForObject("SELECT ts_utc = '2026-10-02T00:00:00Z' FROM observation "
                + "WHERE variable = 'precipitation_daily_total'", Boolean.class)).isTrue();

        // vazamento: alterar leitura posterior a t não muda a linha t
        adapter.setBatches(List.of(rec("S1", "2026-10-01T15:01", r("precipitation", "PT15M", 99.0))));
        refetch(S);
        assertThat(hourly("precipitation", "2026-10-01T15:00:00Z").get("value")).isEqualTo(15.0);
    }

    private Map<String, Object> hourly(String variable, String hourEnd) {
        return jdbc.queryForMap("SELECT value, n_readings FROM observation_hourly_v WHERE station_id = 'S1' "
                + "AND variable = ? AND hour_end_utc = ?::timestamptz", variable, hourEnd);
    }

    @Test
    void absentInRefetchNaoApagaOConsolidado() {
        adapter.setBatches(List.of(
                rec("S1", "2026-10-01T05:00", r("precipitation", "PT1H", 1.0)),
                rec("S1", "2026-10-01T06:00", r("precipitation", "PT1H", 2.0))));
        refetch(S);
        adapter.setBatches(List.of(rec("S1", "2026-10-01T05:00", r("precipitation", "PT1H", 1.0))));
        IngestionRunView run = refetch(S);
        assertThat(run.absentInRefetch()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM observation WHERE status = 'ACTIVE'")).isEqualTo(2);
    }

    @Test
    void brutoEhAppendOnly() {
        adapter.setBatches(List.of(rec("S1", "2026-10-01T05:00", r("precipitation", "PT1H", 1.0))));
        refetch(S);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("DELETE FROM raw_observation"))
                .hasMessageContaining("append-only");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("UPDATE raw_observation SET source_tz = 'X'"))
                .hasMessageContaining("append-only");
    }
}
