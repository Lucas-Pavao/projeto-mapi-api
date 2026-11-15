package com.projeto.mapi.service.sensor.cemaden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.dto.MeasuredRainAccumulationsDTO;
import com.projeto.mapi.dto.StationAccumulationDTO;
import com.projeto.mapi.exception.ServiceUnavailableException;
import com.projeto.mapi.repository.station.StationCanonicalRepository;
import com.projeto.mapi.repository.station.StationCanonicalRepository.CanonicalView;
import com.projeto.mapi.service.ingestion.adapter.ped.CemadenPedClient;
import com.projeto.mapi.service.sensor.cemaden.impl.MeasuredRainAccumulationServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Resposta real da PED (2026-11-15, Recife) reduzida: null ≠ 0; 202 = sem dado; datahora em UTC sem sufixo. */
class MeasuredRainAccumulationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-15T17:30:00Z");

    private final CemadenPedClient client = mock(CemadenPedClient.class);
    /** Repositório em memória (o Mockito desta JVM não instrumenta a classe concreta). */
    private static final class FakeRepo extends StationCanonicalRepository {
        List<CanonicalView> canonicals = List.of();
        Map<String, Map<String, String>> aliases = Map.of();
        Map<String, Instant> last = Map.of();

        FakeRepo() {
            super(null);
        }

        @Override
        public List<CanonicalView> canonicals() {
            return canonicals;
        }

        @Override
        public Map<String, String> canonicalByStationId(String source) {
            return aliases.getOrDefault(source, Map.of());
        }

        @Override
        public Map<String, Instant> lastReadingByCanonical(Instant since) {
            return last;
        }
    }

    private final FakeRepo repo = new FakeRepo();
    private final IngestionProperties props = new IngestionProperties();
    private final MeasuredRainAccumulationServiceImpl service = new MeasuredRainAccumulationServiceImpl(
            client, repo, props, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), new NoUnification());

    private static final class NoUnification implements com.projeto.mapi.service.station.StationUnificationService {
        @Override
        public com.projeto.mapi.dto.StationUnificationReportDTO unify() {
            return null;
        }

        @Override
        public List<com.projeto.mapi.dto.CanonicalStationDTO> canonicalStations() {
            return List.of();
        }
    }

    private static CanonicalView canonical(String id, String ibge) {
        return new CanonicalView(id, "Estação " + id, -8.0, -34.9, "RECIFE", ibge, "CEMADEN_PED",
                List.of("CEMADEN_PED:" + id, "CEMADEN:" + id));
    }

    @Test
    void semLeituraAinda_503() {
        assertThatThrownBy(service::current).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void nuloFicaNulo_zeroFicaZero_202ViraNoData_eDesconhecidaEntraSemCoordenada() {
        repo.canonicals = List.of(canonical("261160612A", "2611606"), canonical("261160614A", "2611606"),
                canonical("260960001A", "2609600"));
        repo.aliases = Map.of("CEMADEN_PED", Map.of("261160612A", "261160612A", "261160614A", "261160614A",
                "260960001A", "260960001A"));
        repo.last = Map.of("261160612A", Instant.parse("2026-11-15T17:20:00Z"));
        when(client.recentAccumulations("2611606")).thenReturn(new CemadenPedClient.Response(200, "u", """
                [{"acc120hr":9.27,"acc12hr":0.0,"acc1hr":0.0,"acc24hr":0.0,"acc3hr":0.0,"acc48hr":0.0,"acc6hr":0.0,
                  "acc72hr":8.47,"acc96hr":9.27,"codestacao":"261160612A","codibge":2611606,
                  "datahora":"2026-11-15T17:27:20.828520","id_estacao":6529},
                 {"acc120hr":13.84,"acc12hr":null,"acc1hr":null,"acc24hr":null,"acc3hr":null,"acc48hr":0.0,"acc6hr":null,
                  "acc72hr":0.4,"acc96hr":0.4,"codestacao":"261160614A","codibge":2611606,
                  "datahora":"2026-11-15T17:27:20.828537","id_estacao":6531},
                 {"acc120hr":1.0,"acc12hr":0.0,"acc1hr":0.0,"acc24hr":0.0,"acc3hr":0.0,"acc48hr":0.0,"acc6hr":0.0,
                  "acc72hr":0.0,"acc96hr":1.0,"codestacao":"A301","codibge":2611606,
                  "datahora":"2026-11-15T17:27:20.828600","id_estacao":1}]
                """, false));
        when(client.recentAccumulations("2609600")).thenReturn(new CemadenPedClient.Response(202, "u",
                "Nenhum resultado foi encontrado", true));

        service.refresh();
        MeasuredRainAccumulationsDTO r = service.current();

        assertThat(r.computedAtUtc()).isEqualTo(Instant.parse("2026-11-15T17:27:20.828600Z"));
        assertThat(r.timezone()).isEqualTo("UTC");
        assertThat(r.rainOrigin()).isEqualTo("MEASURED");
        assertThat(r.attribution()).isEqualTo("DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC");
        assertThat(r.municipalitiesWithoutData()).containsExactly("2609600");
        Map<String, StationAccumulationDTO> by = new java.util.HashMap<>();
        r.stations().forEach(s -> by.put(s.stationCode() == null ? s.canonicalId() : s.stationCode(), s));

        StationAccumulationDTO dry = by.get("261160612A");
        assertThat(dry.status()).isEqualTo("OK");
        assertThat(dry.acc3hMm()).isEqualTo(0.0);
        assertThat(dry.lastReadingUtc()).isEqualTo(Instant.parse("2026-11-15T17:20:00Z"));

        StationAccumulationDTO silent = by.get("261160614A");
        assertThat(silent.acc3hMm()).isNull(); // sem leitura nas 3 h: não é zero
        assertThat(silent.acc48hMm()).isEqualTo(0.0);
        assertThat(silent.status()).isEqualTo("OK");

        StationAccumulationDTO olinda = by.get("260960001A");
        assertThat(olinda.status()).isEqualTo("NO_DATA");
        assertThat(olinda.acc3hMm()).isNull();
        assertThat(olinda.acc120hMm()).isNull();

        StationAccumulationDTO unknown = by.get("A301");
        assertThat(unknown.canonicalId()).isNull();
        assertThat(unknown.lat()).isNull();
    }

    @Test
    void falhaDeUmMunicipioNaoDerrubaOsOutros() {
        repo.canonicals = List.of(canonical("261160612A", "2611606"), canonical("260960001A", "2609600"));
        when(client.recentAccumulations("2611606")).thenThrow(new com.projeto.mapi.exception.SourceUnavailableException("x"));
        when(client.recentAccumulations("2609600")).thenReturn(new CemadenPedClient.Response(202, "u", "", true));
        service.refresh();
        assertThat(service.current().municipalitiesFailed()).containsExactly("2611606");
        assertThat(service.current().stations()).allMatch(s -> s.status().equals("NO_DATA"));
    }
}
