package com.projeto.mapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.controller.SensorController;
import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.service.ingestion.live.LiveSensorStagingService;
import com.projeto.mapi.support.AbstractIngestionTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T9 do ADR 0006 e quarentena TZ_UNVERIFIED das fontes ao vivo (ANA_SOAP, APAC_METEO24H, CEMADEN).
 * O fluxo legado (sensor_data) é simulado gravando o mesmo envelope em raw_data, como o SensorServiceImpl faz.
 */
class LiveSensorStagingIntegrationTest extends AbstractIngestionTest {

    @Autowired private LiveSensorStagingService staging;
    @Autowired private SensorDataRepository sensorData;
    @Autowired private SensorController controller;
    @Autowired private ObjectMapper mapper;

    private JsonNode ana() throws Exception {
        return mapper.readTree("""
                {"codigoestacao":"39187998","id_sensor":"ANA-TELE-RECIFE","Data_Hora_Medicao":"2026-10-10 11:00:00",
                 "Chuva_Adotada":"2,5","Cota_Adotada":120.0}""");
    }

    private JsonNode cemaden() throws Exception {
        return mapper.readTree("""
                {"codigo":"260790201A","id_sensor":"APAC-PLUVIO-RECIFE-JARDIM","data_hora":"2026-10-10 11:10:00",
                 "chuva_acumulada":1.2,"fonte":"APAC/Cemaden"}""");
    }

    private JsonNode meteo() throws Exception {
        return mapper.readTree("""
                {"codigo":"2607901","id_sensor":"APAC-METEO-RECIFE-CURADO","data_hora":"2026-10-10 11:20:00",
                 "temperatura_ar":28.4,"precipitacao_acumulada":0.0,"fonte":"APAC/Meteorologia24h"}""");
    }

    @Test
    void t9_fusoNaoVerificadoVaiParaQuarentena_eCemadenVerificadoConsolida() throws Exception {
        assertThat(staging.stage("ANA_SOAP", List.of(ana()))).isEqualTo(1);
        assertThat(staging.stage("CEMADEN", List.of(cemaden()))).isEqualTo(1);
        assertThat(staging.stage("APAC_METEO24H", List.of(meteo()))).isEqualTo(1);

        assertThat(count("SELECT count(*) FROM raw_observation")).isEqualTo(3);
        assertThat(count("SELECT count(DISTINCT source) FROM raw_observation")).isEqualTo(3);
        // ANA e APAC meteorologia24h seguem UNVERIFIED (F2-DADOS-07 não as aprovou): quarentena TZ_UNVERIFIED
        assertThat(count("SELECT count(*) FROM raw_observation WHERE source_tz = 'UNVERIFIED'")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM ingestion_quarantine WHERE reason = 'TZ_UNVERIFIED' AND resolved_at IS NULL"))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM observation WHERE source IN ('ANA_SOAP', 'APAC_METEO24H')")).isZero();
        // CEMADEN foi verificado (V13, UTC-3): 11:10 local = 14:10Z, consolidado sem quarentena
        assertThat(count("SELECT count(*) FROM raw_observation WHERE source = 'CEMADEN' AND source_tz = 'America/Recife'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ts_utc FROM observation WHERE source = 'CEMADEN' AND variable = 'precipitation'",
                java.sql.Timestamp.class).toInstant()).isEqualTo(java.time.Instant.parse("2026-10-10T14:10:00Z"));
        assertThat(count("SELECT count(*) FROM ingestion_run WHERE kind = 'LIVE' AND status = 'OK' AND quarantined = 1"))
                .isEqualTo(2);
    }

    @Test
    void t9_provenanceIdAponta_paraRunRealDaMesmaFonte() throws Exception {
        sensorData.deleteAll();
        JsonNode e = ana();
        staging.stage("ANA_SOAP", List.of(e));
        sensorData.save(SensorData.builder().sensorId("ANA-TELE-RECIFE").code("39187998").value(2.5)
                .timestamp(LocalDateTime.of(2026, 10, 10, 11, 0)).rawData(e.toString()).build());

        SensorResponseDTO dto = controller.getLatestBySensorId("ANA-TELE-RECIFE").getBody();

        assertThat(dto).isNotNull();
        assertThat(dto.getProvenanceId()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT source FROM ingestion_run WHERE id = ?", String.class,
                UUID.fromString(dto.getProvenanceId()))).isEqualTo("ANA_SOAP");
        assertThat(dto.getRevision()).isNull(); // sem observação consolidada enquanto o fuso é UNVERIFIED
    }

    @Test
    void fonteSemAdaptadorNaoQuebraOFluxoLegado() throws Exception {
        assertThat(staging.stage("FONTE_INEXISTENTE", List.of(ana()))).isZero();
        assertThat(staging.stage("ANA_SOAP", List.of())).isZero();
    }

    @Test
    void depoisDeVerificarOFusoORevalidateConsolidaOBruto() throws Exception {
        // volta ao estado anterior à V13 para reproduzir o caminho real: bruto em quarentena e depois REVALIDATE
        setTz("ANA_SOAP", "UNVERIFIED", "UNVERIFIED");
        // só chuva: o nível da ANA vem sem unidade (cm) e fica fora da faixa em m do catálogo, assunto à parte do fuso
        staging.stage("ANA_SOAP", List.of(mapper.readTree("""
                {"codigo":"39187998","Data_Hora_Medicao":"2026-10-10 11:00:00","Chuva_Adotada":"2,5"}""")));
        assertThat(count("SELECT count(*) FROM observation")).isZero();
        assertThat(count("SELECT count(*) FROM ingestion_quarantine WHERE resolved_at IS NULL")).isEqualTo(1);
        // fuso verificado => REVALIDATE do bruto, sem rebuscar na fonte (11:00 UTC-3 = 14:00Z)
        setTz("ANA_SOAP", "America/Recife", "VERIFIED");
        var run = revalidate("ANA_SOAP", java.time.Instant.parse("2026-10-10T00:00:00Z"), java.time.Instant.parse("2026-10-11T00:00:00Z"));
        assertThat(run.status()).isEqualTo("OK");
        assertThat(jdbc.queryForObject("SELECT ts_utc FROM observation WHERE source = 'ANA_SOAP' AND variable = 'precipitation'",
                java.sql.Timestamp.class).toInstant()).isEqualTo(java.time.Instant.parse("2026-10-10T14:00:00Z"));
        assertThat(count("SELECT count(*) FROM ingestion_quarantine WHERE resolved_at IS NULL")).isZero();
        setTz("ANA_SOAP", "UNVERIFIED", "UNVERIFIED");
    }
}
