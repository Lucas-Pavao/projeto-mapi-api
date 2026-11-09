package com.projeto.mapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.ingestion.adapter.OpenMeteoArchiveAdapter;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tabela e resumo de eventos de chuva (ADR 0017) contra TimescaleDB real (ADR 0004), passando pela grade horária
 * {@code observation_hourly_v}. Reanálise (PT1H) para os casos de segmentação; ANA (PT15M) para a regra das 4 leituras
 * e o pico de 15 min.
 */
class RainEventIntegrationTest extends AbstractApiIntegrationTest {

    private static final String SLUG = "TESTE_EVENTOS_CHUVA";
    private static final double LAT = -9.4321, LON = -36.1234;
    private static final String OM = "OPEN_METEO_ARCHIVE";
    private static final String OM_STATION = OpenMeteoArchiveAdapter.stationId(LAT, LON);
    private static final String ANA_STATION = "TESTE-ANA-EV";

    /** Janela padrão e série seca que a cobre com folga (72 h + 6 h antes, 6 h depois). */
    private static final Instant FROM = Instant.parse("2024-03-10T00:00:00Z");
    private static final Instant TO = Instant.parse("2024-03-20T00:00:00Z");

    @Autowired JdbcTemplate jdbc;
    private UUID run;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM observation WHERE station_id IN (?, ?)", OM_STATION, ANA_STATION);
        jdbc.update("""
                INSERT INTO flood_points (name, slug, latitude, longitude) SELECT 'teste eventos', ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM flood_points WHERE slug = ?)""", SLUG, LAT, LON, SLUG);
        run = ObservationFixtures.run(jdbc, OM);
        ObservationFixtures.station(jdbc, OM, OM_STATION, LAT, LON);
        ObservationFixtures.station(jdbc, "ANA_SOAP", ANA_STATION, LAT + 0.03, LON); // ~3,3 km: dentro do raio de 5 km
    }

    /** Horas com fim em [first, last] da reanálise; valores do mapa substituem o zero. */
    private void omSeries(Instant first, Instant last, Map<Instant, Double> wet) {
        for (Instant h = first; !h.isAfter(last); h = h.plus(Duration.ofHours(1))) {
            if (wet.containsKey(h) && wet.get(h) == null) continue; // lacuna: sem linha
            ObservationFixtures.precipitation(jdbc, run, OM, OM_STATION, h, wet.getOrDefault(h, 0.0));
        }
    }

    private void omDryWindowWith(Map<Instant, Double> wet) {
        omSeries(FROM.minus(Duration.ofHours(100)), TO.plus(Duration.ofHours(10)), wet);
    }

    private static Instant h(String iso) {
        return Instant.parse(iso);
    }

    private MvcResult call(String path, String source, Instant from, Instant to, String format) throws Exception {
        return mockMvc.perform(as(get("/api/analise/eventos-chuva" + path)
                        .param("slug", SLUG).param("from", from.toString()).param("to", to.toString())
                        .param("source", source).param("format", format), userToken()))
                .andExpect(status().isOk()).andReturn();
    }

    private JsonNode events(Instant from, Instant to) throws Exception {
        return objectMapper.readTree(call("", "OPEN_METEO_ARCHIVE", from, to, "json").getResponse().getContentAsString());
    }

    @Test
    void eventoSimples() throws Exception {
        omDryWindowWith(Map.of(h("2024-03-12T10:00:00Z"), 2.0, h("2024-03-12T11:00:00Z"), 6.0,
                h("2024-03-12T12:00:00Z"), 1.0));
        JsonNode body = events(FROM, TO);
        JsonNode ev = body.get("events");
        assertThat(ev).hasSize(1);
        JsonNode e = ev.get(0);
        assertThat(e.get("startUtc").asText()).isEqualTo("2024-03-12T09:00:00Z");
        assertThat(e.get("endUtc").asText()).isEqualTo("2024-03-12T12:00:00Z");
        assertThat(e.get("durationMin").asInt()).isEqualTo(180);
        assertThat(e.get("totalMm").asDouble()).isEqualTo(9.0);
        assertThat(e.get("maxHourlyMmH").asDouble()).isEqualTo(6.0);
        assertThat(e.get("type").asText()).isEqualTo("MEDIA"); // 6 >= 0,1 × 50,3
        assertThat(e.get("hasGap").asBoolean()).isFalse();
        assertThat(e.get("truncated").asBoolean()).isFalse();
        assertThat(e.get("max15minMmH").isNull()).isTrue(); // reanálise não tem sub-horário
        assertThat(e.get("antecedentRain24h").asDouble()).isEqualTo(0.0);
        assertThat(e.get("estimatedInflowMm").asDouble()).isEqualTo(7.74); // 9 × 0,86
        assertThat(e.get("startLocal").asText()).isEqualTo("2024-03-12T06:00-03:00");
        assertThat(e.get("provenanceRunIds").get(0).asText()).isEqualTo(run.toString());
        assertThat(body.get("manifest").get("params").get("dryGapHours").asInt()).isEqualTo(6);
        assertThat(body.get("manifest").get("source").asText()).isEqualTo(OM);
    }

    @Test
    void separadosExatamentePeloIntervaloSecoSaoDois() throws Exception {
        omDryWindowWith(Map.of(h("2024-03-12T10:00:00Z"), 3.0, h("2024-03-12T17:00:00Z"), 3.0));
        JsonNode ev = events(FROM, TO).get("events");
        assertThat(ev).hasSize(2);
        assertThat(ev.get(1).get("dryHoursBefore").asDouble()).isEqualTo(6.0);
    }

    @Test
    void separadosPorUmaHoraAMenosViramUm() throws Exception {
        omDryWindowWith(Map.of(h("2024-03-12T10:00:00Z"), 3.0, h("2024-03-12T16:00:00Z"), 3.0));
        JsonNode ev = events(FROM, TO).get("events");
        assertThat(ev).hasSize(1);
        assertThat(ev.get(0).get("durationMin").asInt()).isEqualTo(7 * 60);
    }

    @Test
    void eventoAtravessandoAMeiaNoiteLocal() throws Exception {
        // meia-noite em Recife = 03:00Z; horas com fim 02:00Z..05:00Z = 23:00 a 02:00 local
        Map<Instant, Double> wet = new TreeMap<>();
        for (int k = 2; k <= 5; k++) wet.put(h("2024-03-13T0" + k + ":00:00Z"), 4.0);
        omDryWindowWith(wet);
        JsonNode ev = events(FROM, TO).get("events");
        assertThat(ev).hasSize(1);
        assertThat(ev.get(0).get("startLocal").asText()).isEqualTo("2024-03-12T22:00-03:00");
        assertThat(ev.get(0).get("endLocal").asText()).isEqualTo("2024-03-13T02:00-03:00");
        assertThat(ev.get(0).get("month").asInt()).isEqualTo(3);
        assertThat(ev.get(0).get("season").asText()).isEqualTo("OUTONO");
    }

    @Test
    void eventoComLacunaNoMeio() throws Exception {
        Map<Instant, Double> wet = new java.util.HashMap<>();
        wet.put(h("2024-03-12T10:00:00Z"), 3.0);
        wet.put(h("2024-03-12T11:00:00Z"), null); // hora sem leitura
        wet.put(h("2024-03-12T12:00:00Z"), 3.0);
        omDryWindowWith(wet);
        JsonNode ev = events(FROM, TO).get("events");
        assertThat(ev).hasSize(1);
        assertThat(ev.get(0).get("hasGap").asBoolean()).isTrue();
        assertThat(ev.get(0).get("expectedReadings").asInt()).isEqualTo(3);
        assertThat(ev.get(0).get("receivedReadings").asInt()).isEqualTo(2);
        assertThat(ev.get(0).get("totalMm").asDouble()).isEqualTo(6.0);
    }

    @Test
    void eventoNaBordaDaJanelaEhTruncadoEFicaForaDoResumo() throws Exception {
        omDryWindowWith(Map.of(h("2024-03-12T09:00:00Z"), 5.0, h("2024-03-12T10:00:00Z"), 5.0,
                h("2024-03-12T11:00:00Z"), 5.0));
        Instant from = h("2024-03-12T09:30:00Z");
        JsonNode ev = events(from, TO).get("events");
        assertThat(ev).hasSize(1);
        assertThat(ev.get(0).get("truncated").asBoolean()).isTrue();
        JsonNode rows = objectMapper.readTree(call("/resumo", "OPEN_METEO_ARCHIVE", from, TO, "json")
                .getResponse().getContentAsString()).get("rows");
        JsonNode total = rows.get(3);
        assertThat(total.get("type").asText()).isEqualTo("TOTAL");
        assertThat(total.get("count").asInt()).isZero();
        assertThat(total.get("truncatedExcluded").asInt()).isEqualTo(1);
    }

    @Test
    void serieTodaSeca() throws Exception {
        omDryWindowWith(Map.of());
        JsonNode body = events(FROM, TO);
        assertThat(body.get("events")).isEmpty();
        JsonNode total = objectMapper.readTree(call("/resumo", "OPEN_METEO_ARCHIVE", FROM, TO, "json")
                .getResponse().getContentAsString()).get("rows").get(3);
        assertThat(total.get("count").asInt()).isZero();
        assertThat(total.get("coverageFraction").asDouble()).isEqualTo(1.0);
        assertThat(total.get("fractionOfTime").asDouble()).isEqualTo(0.0);
    }

    @Test
    void mesmoIntervaloDuasVezesDaOMesmoDatasetId() throws Exception {
        omDryWindowWith(Map.of(h("2024-03-12T10:00:00Z"), 3.0, h("2024-03-15T10:00:00Z"), 30.0));
        MvcResult a = call("", "OPEN_METEO_ARCHIVE", FROM, TO, "csv");
        MvcResult b = call("", "OPEN_METEO_ARCHIVE", FROM, TO, "csv");
        String idA = a.getResponse().getHeader("X-Dataset-Id");
        assertThat(idA).isNotBlank().isEqualTo(b.getResponse().getHeader("X-Dataset-Id"));
        assertThat(idA).isEqualTo(Hashing.sha256(a.getResponse().getContentAsString()));
        // o JSON carrega o mesmo id no cabeçalho e no manifesto
        MvcResult j = call("", "OPEN_METEO_ARCHIVE", FROM, TO, "json");
        assertThat(j.getResponse().getHeader("X-Dataset-Id")).isEqualTo(idA);
        assertThat(objectMapper.readTree(j.getResponse().getContentAsString()).get("manifest").get("datasetId").asText())
                .isEqualTo(idA);
        // e o resumo também é estável
        assertThat(call("/resumo", "OPEN_METEO_ARCHIVE", FROM, TO, "csv").getResponse().getHeader("X-Dataset-Id"))
                .isEqualTo(call("/resumo", "OPEN_METEO_ARCHIVE", FROM, TO, "csv").getResponse().getHeader("X-Dataset-Id"));
    }

    @Test
    void anaUsaQuatroLeiturasEPicoDeQuinzeMinutos() throws Exception {
        UUID anaRun = ObservationFixtures.run(jdbc, "ANA_SOAP");
        Instant first = FROM.minus(Duration.ofHours(100));
        Instant last = TO.plus(Duration.ofHours(10));
        Instant wetHour = h("2024-03-12T11:00:00Z"); // leituras 10:15, 10:30, 10:45, 11:00
        Instant gapHour = h("2024-03-14T11:00:00Z"); // só 3 leituras: hora vira lacuna
        for (Instant t = first.plus(Duration.ofMinutes(15)); !t.isAfter(last); t = t.plus(Duration.ofMinutes(15))) {
            if (t.equals(gapHour.minus(Duration.ofMinutes(30)))) continue;
            double v = 0.0;
            if (!t.isAfter(wetHour) && t.isAfter(wetHour.minus(Duration.ofHours(1)))) {
                v = t.equals(wetHour.minus(Duration.ofMinutes(30))) ? 6.0 : 1.0;
            }
            ObservationFixtures.precipitation(jdbc, anaRun, "ANA_SOAP", ANA_STATION, "PT15M", t, v, "ACTIVE");
        }
        JsonNode body = objectMapper.readTree(call("", "ANA", FROM, TO, "json").getResponse().getContentAsString());
        JsonNode ev = body.get("events");
        assertThat(ev).hasSize(1);
        JsonNode e = ev.get(0);
        assertThat(e.get("totalMm").asDouble()).isEqualTo(9.0);
        assertThat(e.get("max15minMmH").asDouble()).isEqualTo(24.0); // 6 mm em 15 min × 4
        assertThat(e.get("expectedReadings").asInt()).isEqualTo(4);
        assertThat(e.get("receivedReadings").asInt()).isEqualTo(4);
        assertThat(e.get("stationId").asText()).isEqualTo(ANA_STATION);
        assertThat(e.get("stationDistanceM").asDouble()).isBetween(3000.0, 3500.0); // 0,03° de latitude
        assertThat(e.get("rainOrigin").asText()).isEqualTo("MEASURED");
        // a hora com 3 leituras é lacuna: cobertura abaixo de 1
        JsonNode total = objectMapper.readTree(call("/resumo", "ANA", FROM, TO, "json").getResponse()
                .getContentAsString()).get("rows").get(3);
        assertThat(total.get("coverageFraction").asDouble()).isLessThan(1.0);
        assertThat(body.get("manifest").get("source").asText()).isEqualTo("ANA_SOAP");
        jdbc.update("DELETE FROM observation WHERE station_id = ?", ANA_STATION);
    }

    @Test
    void pluviometroForaDoRaioCaiNaReanaliseDeclarada() throws Exception {
        // ADR 0018: estação ANA a ~6,7 km (fora dos 5 km); o ponto usa a reanálise, com a origem na linha
        ObservationFixtures.station(jdbc, "ANA_SOAP", ANA_STATION, LAT + 0.06, LON);
        UUID anaRun = ObservationFixtures.run(jdbc, "ANA_SOAP");
        ObservationFixtures.precipitation(jdbc, anaRun, "ANA_SOAP", ANA_STATION, "PT15M", h("2024-03-12T10:00:00Z"), 1.0,
                "ACTIVE");
        omDryWindowWith(Map.of(h("2024-03-12T10:00:00Z"), 4.0));
        JsonNode body = objectMapper.readTree(call("", "ANA", FROM, TO, "json").getResponse().getContentAsString());
        JsonNode e = body.get("events").get(0);
        assertThat(e.get("rainOrigin").asText()).isEqualTo("REANALYSIS_FALLBACK");
        assertThat(e.get("source").asText()).isEqualTo(OM);
        assertThat(e.get("stationId").asText()).isEqualTo(OM_STATION);
        assertThat(body.get("manifest").get("params").get("sensorLinkageRadiusM").asDouble()).isEqualTo(5000.0);
        assertThat(body.get("manifest").get("params").get("sensorLinkageConfigId").isNumber()).isTrue();
        jdbc.update("DELETE FROM observation WHERE station_id = ?", ANA_STATION);
    }

    @Test
    void semTokenDa403ParametroInvalido400PontoInexistente404() throws Exception {
        mockMvc.perform(get("/api/analise/eventos-chuva").param("slug", SLUG)
                .param("from", FROM.toString()).param("to", TO.toString())).andExpect(status().isForbidden()); // padrão do projeto sem token
        mockMvc.perform(as(get("/api/analise/eventos-chuva").param("slug", SLUG).param("from", FROM.toString())
                .param("to", TO.toString()).param("source", "APAC"), userToken())).andExpect(status().isBadRequest());
        mockMvc.perform(as(get("/api/analise/eventos-chuva").param("slug", "NAO_EXISTE").param("from", FROM.toString())
                .param("to", TO.toString()), userToken())).andExpect(status().isNotFound());
    }
}
