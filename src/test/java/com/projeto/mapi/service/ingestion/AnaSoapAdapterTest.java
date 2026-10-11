package com.projeto.mapi.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.ingestion.adapter.AnaLegacyClient;
import com.projeto.mapi.service.ingestion.adapter.AnaSoapAdapter;
import com.projeto.mapi.service.ingestion.validation.TimestampNormalizer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Carga histórica da ANA: o bruto sai com o rótulo original e fuso America/Recife (F2-DADOS-10); o fuso não é convertido no adaptador. */
class AnaSoapAdapterTest {

    private static final String SERIES = """
            <?xml version="1.0" encoding="utf-8"?>
            <DataTable xmlns="http://MRCS/"><diffgr:diffgram xmlns:diffgr="urn:schemas-microsoft-com:xml-msdata">
            <DocumentElement xmlns="">
              <DadosHidrometereologicos><CodEstacao>39187800</CodEstacao><DataHora>2025-06-30 23:45:00 </DataHora>
                <Vazao>24.30</Vazao><Nivel>239.00</Nivel><Chuva>0.40</Chuva></DadosHidrometereologicos>
              <DadosHidrometereologicos><CodEstacao>39187800</CodEstacao><DataHora>2025-06-30 23:30:00 </DataHora>
                <Vazao /><Nivel /><Chuva>0.00</Chuva></DadosHidrometereologicos>
            </DocumentElement></diffgr:diffgram></DataTable>
            """;
    private static final String INVENTORY = """
            <?xml version="1.0" encoding="utf-8"?>
            <DataSet xmlns="http://MRCS/"><diffgr:diffgram xmlns:diffgr="urn:schemas-microsoft-com:xml-msdata">
            <Estacoes xmlns=""><Table><NomeEstacao>SÃO LOURENÇO DA MATA II</NomeEstacao><CodEstacao>39187800</CodEstacao>
              <Municipio-UF>SÃO LOURENÇO DA MATA-PE</Municipio-UF><Latitude>-7.9986</Latitude><Longitude>-35.0392</Longitude>
              <NomeRio>RIO CAPIBARIBE</NomeRio><Operadora>SGB</Operadora></Table>
              <Table><NomeEstacao>OUTRA</NomeEstacao><CodEstacao>00000001</CodEstacao></Table></Estacoes>
            </diffgr:diffgram></DataSet>
            """;

    private final List<String> calls = new ArrayList<>();

    private AnaSoapAdapter adapter(Set<String> pluviometers) {
        AnaLegacyClient client = new AnaLegacyClient() {
            @Override
            public Response inventory() {
                return new Response("inv", INVENTORY);
            }

            @Override
            public Response series(String code, LocalDate s, LocalDate e) {
                calls.add(code + " " + s + ".." + e);
                return new Response("u", SERIES);
            }
        };
        IngestionProperties props = new IngestionProperties();
        props.setAnaHistoricalStations(List.of("39187800"));
        FloodPointRepository repo = mock(FloodPointRepository.class);
        FloodPoint p = new FloodPoint();
        p.setPluviometerStationIds(pluviometers);
        when(repo.findAll()).thenReturn(List.of(p));
        return new AnaSoapAdapter(client, props, repo, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void gravaRotuloOriginalComFusoNaoVerificadoEMesesPorEstacao() {
        AnaSoapAdapter a = adapter(Set.of("APAC-PLUVIO-X", "39084000"));
        List<FetchedBatch> batches = new ArrayList<>();
        a.fetch(Instant.parse("2025-05-10T00:00:00Z"), Instant.parse("2025-07-01T00:00:00Z"), batches::add);

        // 2 estações (a configurada e o código de 8 dígitos do ponto; o id APAC é ignorado) x 2 meses
        assertThat(calls).containsExactly("39187800 2025-05-01..2025-05-31", "39187800 2025-06-01..2025-06-30",
                "39084000 2025-05-01..2025-05-31", "39084000 2025-06-01..2025-06-30");
        RawRecord r = batches.get(0).records().get(0);
        assertThat(r.source()).isEqualTo("ANA_SOAP");
        assertThat(r.stationId()).isEqualTo("39187800");
        assertThat(r.sourceTsText()).isEqualTo("2025-06-30 23:45:00");
        assertThat(r.sourceTsNorm()).isEqualTo("2025-06-30T23:45");
        assertThat(r.sourceTz()).isEqualTo("America/Recife");
        assertThat(r.payload().path("Chuva_Adotada").asText()).isEqualTo("0.40");
        assertThat(batches.get(0).records().get(1).payload().has("Cota_Adotada")).isFalse();
        assertThat(batches.get(0).stations()).hasSize(1);
        assertThat(batches.get(0).stations().get(0).name()).isEqualTo("SÃO LOURENÇO DA MATA II");
        // 23:45 em UTC-3 = 02:45 UTC do dia seguinte
        assertThat(TimestampNormalizer.toInstant(r.sourceTsNorm(), r.sourceTz()))
                .isEqualTo(Instant.parse("2025-07-01T02:45:00Z"));
    }

    @Test
    void extraiChuvaComJanelaNaoVerificada() {
        AnaSoapAdapter a = adapter(Set.of());
        List<FetchedBatch> batches = new ArrayList<>();
        a.fetch(Instant.parse("2025-06-01T00:00:00Z"), Instant.parse("2025-07-01T00:00:00Z"), batches::add);

        List<DraftValue> v = a.extract(batches.get(0).records().get(0));
        assertThat(v).anySatisfy(d -> {
            assertThat(d.variable()).isEqualTo("precipitation");
            assertThat(d.value()).isEqualTo(0.4);
            assertThat(d.qualifier()).isEqualTo("WINDOW_UNVERIFIED");
        });
    }

    @Test
    void nivelDoLegadoEmCentimetrosViraMetros() {
        AnaSoapAdapter a = adapter(Set.of());
        List<FetchedBatch> batches = new ArrayList<>();
        a.fetch(Instant.parse("2025-06-01T00:00:00Z"), Instant.parse("2025-07-01T00:00:00Z"), batches::add);

        List<DraftValue> v = a.extract(batches.get(0).records().get(0));
        assertThat(v).filteredOn(d -> d.variable().equals("water_level")).singleElement().satisfies(d -> {
            assertThat(d.value()).isCloseTo(2.39, org.assertj.core.data.Offset.offset(1e-9)); // payload: 239.00 cm
            assertThat(d.qualifier()).isNull();
        });
        // o bruto guarda o número como a ANA entregou
        assertThat(batches.get(0).records().get(0).payload().path("Cota_Adotada").asText()).isEqualTo("239.00");
    }

    @Test
    void nivelDeEnvelopeDeOutraOrigemNaoEConvertido() {
        AnaSoapAdapter a = adapter(Set.of());
        com.fasterxml.jackson.databind.node.ObjectNode env = new ObjectMapper().createObjectNode();
        env.put("codigo", "39187800");
        env.put("Data_Hora_Medicao", "2026-10-10 12:00:00");
        env.put("Cota_Adotada", "239.00");
        env.put("origem", "Hidro-WebService");
        List<DraftValue> v = a.extract(a.toRaw(env, Instant.parse("2026-10-10T16:00:00Z")));
        assertThat(v).singleElement().satisfies(d -> {
            assertThat(d.value()).isEqualTo(239.0);
            assertThat(d.qualifier()).isEqualTo("UNIT_UNVERIFIED");
        });
    }

    @Test
    void naoPedeMesesNoFuturo() {
        AnaSoapAdapter a = adapter(Set.of());
        a.fetch(Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"), b -> {});
        assertThat(calls).containsExactly("39187800 2026-09-01..2026-09-30", "39187800 2026-10-01..2026-10-10");
    }
}
