package com.projeto.mapi.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.service.geocoding.GeocodeLevel;
import com.projeto.mapi.service.geocoding.GeocodeResult;
import com.projeto.mapi.service.geocoding.GeocodingService;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.occurrence.CkanRawRecord;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceIngestionService;
import com.projeto.mapi.support.AbstractIngestionTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-BACK-18 e F2-BACK-20 contra o banco real, com payloads reais do recurso CKAN de 2022 (colunas deslocadas).
 */
class CkanShiftedResourceIntegrationTest extends AbstractIngestionTest {

    private static final String RES_A = "res-2022-a";
    private static final String RES_B = "res-2022-b";

    @Autowired private OccurrenceIngestionService service;
    @MockitoBean private GeocodingService geocoding;

    @BeforeEach
    void setup() {
        jdbc.execute("TRUNCATE flood_point_pluviometers, flood_point_river_levels, flood_point_weather_stations, "
                + "flood_events, flood_points CASCADE");
        jdbc.update("DELETE FROM linkage_config WHERE created_by = 'pipeline'");
        // a rua existe no geocodificador (nível rua); o resto cai no centróide do bairro
        when(geocoding.geocodeWithLevel(any(), any(), any())).thenAnswer(inv -> {
            String addr = inv.getArgument(0);
            if (addr.contains("joaquim ribeiro")) return Optional.of(new GeocodeResult(-8.0900, -34.9300, GeocodeLevel.STREET));
            return Optional.of(new GeocodeResult(-8.0000, -34.9000, GeocodeLevel.NEIGHBORHOOD));
        });
    }

    private List<CkanRawRecord> sample() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/ckan/2022-shifted-sample.json")) {
            List<Map<String, Object>> rows = new ObjectMapper().readValue(in, new TypeReference<>() {});
            return rows.stream().map(m -> new CkanRawRecord(String.valueOf(m.get("_id")), m)).toList();
        }
    }

    @Test
    void recurso2022EntraPeloMapeamentoEOsOutrosFicamForaSemQuarentena() throws Exception {
        List<CkanRawRecord> all = sample();
        List<CkanRawRecord> a = all.subList(0, all.size() - 1);           // recurso com a coluna "Mês"
        List<CkanRawRecord> b = all.subList(all.size() - 1, all.size());  // recurso com a coluna "MÃªs": _id 35006 repetido

        IngestionRunView runA = service.ingest("pkg", RES_A, a);
        assertThat(runA.status()).isEqualTo("OK");
        assertThat(runA.quarantined()).as("data lida do campo certo: nada em TS_UNPARSEABLE").isZero();
        assertThat(runA.valid()).isEqualTo(3);

        // 3 ocorrências de alagamento em 1 dia (2022-07-12, hora local de Recife)
        assertThat(count("SELECT count(*) FROM flood_occurrence")).isEqualTo(3);
        assertThat(count("SELECT count(DISTINCT occurred_from) FROM flood_occurrence "
                + "WHERE occurred_from = '2022-07-12T03:00:00Z' AND temporal_precision = 'DAY'")).isEqualTo(1);
        // endereço = logradouro de "Solicitacao" (sem número), geocodificado em nível de rua, bairro do bruto
        assertThat(jdbc.queryForObject("SELECT address_text FROM flood_occurrence WHERE ckan_record_id = '35006'", String.class))
                .isEqualTo("Avenida Joaquim Ribeiro");
        assertThat(jdbc.queryForObject("SELECT geocode_level FROM flood_occurrence WHERE ckan_record_id = '35006'", String.class))
                .isEqualTo("STREET");
        assertThat(jdbc.queryForObject("SELECT neighborhood FROM flood_occurrence WHERE ckan_record_id = '35006'", String.class))
                .isEqualTo("Varzea");

        // o bruto é preservado como veio (colunas deslocadas incluídas)
        assertThat(count("SELECT count(*) FROM raw_observation WHERE station_id = ? AND payload->>'Data' = 'Alagamentos' "
                + "AND payload->>'Mês' = '2022-07-12'", RES_A + "#35006")).isEqualTo(1);

        // o mesmo _id/dia no outro recurso (duplicado do portal) não vira segunda ocorrência
        IngestionRunView runB = service.ingest("pkg", RES_B, b);
        assertThat(runB.status()).isEqualTo("OK");
        assertThat(count("SELECT count(*) FROM flood_occurrence")).isEqualTo(3);
    }

    @Test
    void enderecoSemRuaUsaOBairroENuncaInventaNivelDeRua() {
        org.mockito.Mockito.reset(geocoding);
        when(geocoding.geocodeWithLevel(any(), any(), any())).thenAnswer(inv -> {
            String addr = inv.getArgument(0);
            if (!addr.isBlank()) return Optional.empty();   // rua ilegível: só o bairro resolve
            return Optional.of(new GeocodeResult(-8.0000, -34.9000, GeocodeLevel.NEIGHBORHOOD));
        });
        service.ingest("pkg", RES_A, List.of(new CkanRawRecord("9", Map.of("_id", "9", "Regional", "2022", "Ano", "Julho",
                "Mês", "2022-07-12", "Data", "Alagamentos", "Ocorrencia", "Vistoria", "Solicitacao", "###",
                "Endereco", "?_$.", "Bairro", "Varzea"))));
        assertThat(jdbc.queryForObject("SELECT geocode_level FROM flood_occurrence WHERE ckan_record_id = '9'", String.class))
                .isEqualTo("NEIGHBORHOOD");
    }

    @Test
    void semEnderecoNemBairroFicaNoneComMetodoNaoGeocodificado() {
        org.mockito.Mockito.reset(geocoding);
        when(geocoding.geocodeWithLevel(any(), any(), any())).thenReturn(Optional.empty());
        service.ingest("pkg", RES_A, List.of(new CkanRawRecord("8", Map.of("_id", "8", "Regional", "2022", "Ano", "Julho",
                "Mês", "2022-07-12", "Data", "Alagamentos", "Ocorrencia", "Vistoria", "Solicitacao", "Rua Sem Achado",
                "Endereco", "?_$.", "Bairro", ""))));
        assertThat(jdbc.queryForObject("SELECT geocode_level FROM flood_occurrence WHERE ckan_record_id = '8'", String.class))
                .isEqualTo("NONE");
        assertThat(jdbc.queryForObject("SELECT geocode_method FROM flood_occurrence WHERE ckan_record_id = '8'", String.class))
                .isEqualTo("NOT_GEOCODED");
        assertThat(jdbc.queryForObject("SELECT neighborhood FROM flood_occurrence WHERE ckan_record_id = '8'", String.class)).isNull();
    }

    @Test
    void revalidateReprocessaOBrutoFechaAQuarentenaAntigaESemTocarNoBruto() throws Exception {
        service.ingest("pkg", RES_A, sample().subList(0, 7));
        long brutos = count("SELECT count(*) FROM raw_observation WHERE source = 'CKAN_RECIFE'");
        // estado herdado do parser antigo: nenhuma ocorrência e todos os brutos em quarentena TS_UNPARSEABLE
        jdbc.update("DELETE FROM flood_occurrence");
        jdbc.update("INSERT INTO ingestion_quarantine (run_id, raw_id, raw_ingested_at, rule_id, rule_version, reason, "
                + "quarantined_at) SELECT run_id, id, ingested_at, 'TS', 'legado', 'TS_UNPARSEABLE', now() FROM raw_observation");
        assertThat(count("SELECT count(*) FROM ingestion_quarantine WHERE resolved_at IS NULL")).isEqualTo(brutos);

        IngestionRunView run = service.revalidate("pkg", RES_A);

        assertThat(run.status()).isEqualTo("OK");
        assertThat(run.mode()).isEqualTo("REVALIDATE");
        assertThat(run.fetched()).isEqualTo(7);
        assertThat(run.rawInserted()).as("o bruto não é regravado").isZero();
        assertThat(run.rawUnchanged()).isEqualTo(7);
        assertThat(count("SELECT count(*) FROM raw_observation WHERE source = 'CKAN_RECIFE'")).isEqualTo(brutos);
        assertThat(count("SELECT count(*) FROM flood_occurrence")).isEqualTo(3);
        // quarentenas fechadas (com motivo), não apagadas
        assertThat(count("SELECT count(*) FROM ingestion_quarantine WHERE resolved_at IS NULL")).isZero();
        assertThat(count("SELECT count(*) FROM ingestion_quarantine WHERE resolution LIKE 'REVALIDATED:%'")).isEqualTo(brutos);

        // idempotente
        IngestionRunView again = service.revalidate("pkg", RES_A);
        assertThat(again.status()).isEqualTo("OK");
        assertThat(count("SELECT count(*) FROM flood_occurrence")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM flood_occurrence WHERE revision = 1")).isEqualTo(3);
    }

    @Test
    void backfillPreencheBairroDoBrutoENivelSoQuandoVerificavel() {
        // legado: ocorrências gravadas antes da V11 (nível nulo): uma com coordenada que o geocodificador repete,
        // uma com coordenada que ele não repete e uma sem coordenada
        service.ingest("pkg", "res-L", List.of(
                legacy("1", "Rua Igual, 10", "Boa Viagem"),
                legacy("2", "Rua Mudou, 10", "Pina"),
                legacy("3", "Rua Nula, 10", "Imbiribeira")));
        jdbc.update("UPDATE flood_occurrence SET geocode_level = NULL, neighborhood = NULL, geocoded_lat = -8.01, "
                + "geocoded_lon = -34.91, geocode_method = 'GEOCODING_SERVICE' WHERE ckan_record_id IN ('1', '2')");
        jdbc.update("UPDATE flood_occurrence SET geocode_level = NULL, neighborhood = NULL, geocoded_lat = NULL, "
                + "geocoded_lon = NULL, geocode_method = 'NOT_GEOCODED' WHERE ckan_record_id = '3'");
        org.mockito.Mockito.reset(geocoding);
        when(geocoding.geocodeWithLevel(any(), any(), any())).thenAnswer(inv -> {
            String addr = inv.getArgument(0);
            if (addr.contains("igual")) return Optional.of(new GeocodeResult(-8.01, -34.91, GeocodeLevel.NEIGHBORHOOD));
            return Optional.of(new GeocodeResult(-8.50, -34.50, GeocodeLevel.STREET));   // outra coordenada: não confere
        });

        OccurrenceIngestionService.GeocodeLevelBackfill r = service.backfillGeocodeLevels();

        assertThat(r.examined()).isEqualTo(3);
        assertThat(r.neighborhood()).isEqualTo(1);
        assertThat(r.none()).isEqualTo(1);
        assertThat(r.unverifiable()).isEqualTo(1);
        assertThat(r.neighborhoodFilled()).isEqualTo(3);
        assertThat(level("1")).isEqualTo("NEIGHBORHOOD");
        assertThat(level("2")).as("coordenada não reproduzível: sem nível inventado").isNull();
        assertThat(level("3")).isEqualTo("NONE");
        assertThat(jdbc.queryForObject("SELECT neighborhood FROM flood_occurrence WHERE ckan_record_id = '2'", String.class))
                .isEqualTo("Pina");
        // segunda passada: só o que ainda é nulo é reexaminado
        assertThat(service.backfillGeocodeLevels().examined()).isEqualTo(1);
    }

    private String level(String recordId) {
        return jdbc.queryForObject("SELECT geocode_level FROM flood_occurrence WHERE ckan_record_id = ?", String.class, recordId);
    }

    private static CkanRawRecord legacy(String id, String address, String bairro) {
        return new CkanRawRecord(id, Map.of("_id", id, "Ocorrencia", "Alagamentos", "Data", "2024-05-28T00:00:00",
                "Endereco", address, "Bairro", bairro));
    }
}
