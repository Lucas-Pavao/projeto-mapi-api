package com.projeto.mapi.service.sensor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.dto.CemadenAccumulationDTO;
import com.projeto.mapi.exception.ServiceUnavailableException;
import com.projeto.mapi.exception.SourceUnavailableException;
import com.projeto.mapi.repository.ingestion.ObservationReadRepository;
import com.projeto.mapi.repository.ingestion.ObservationReadRepository.StationRainSum;
import com.projeto.mapi.service.sensor.cemaden.CemadenAccumulationClient;
import com.projeto.mapi.service.sensor.cemaden.impl.CemadenAccumulationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CemadenAccumulationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-22T00:45:00Z");
    private final ObjectMapper mapper = new ObjectMapper();
    private CemadenAccumulationClient client;
    /** Somas que o repositório devolve (o Mockito do JDK 25 não simula a classe concreta). */
    private List<StationRainSum> sums = List.of();
    private final ObservationReadRepository observations = new ObservationReadRepository(null) {
        @Override
        public List<StationRainSum> rainSumByStation(String source, Instant fromUtc, Instant toUtc) {
            return "CEMADEN".equals(source) ? sums : List.of();
        }
    };
    private CemadenAccumulationServiceImpl service;

    @BeforeEach
    void setUp() {
        client = mock(CemadenAccumulationClient.class);
        service = new CemadenAccumulationServiceImpl(client, observations, new AppProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private JsonNode panel(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void casaPeloNomeTrazConferenciaETrataTracoPelaJanela() throws Exception {
        when(client.fetchState("PE")).thenReturn(List.of(
                panel("""
                        {"cidade":"RECIFE","nomeestacao":"Alto Mandu","ultimovalor":0.2,"datahoraUltimovalor":"22/10/26 00:20",
                         "acc1hr":0.2,"acc3hr":0.59,"acc6hr":0.59,"acc12hr":0.59,"acc24hr":4.73,"acc48hr":4.73,
                         "acc72hr":4.73,"acc96hr":4.73,"tipoestacao":1}"""),
                // última leitura há 9 h: "-" vale 0 em 12 h ou mais e nulo nas janelas menores
                panel("""
                        {"cidade":"RECIFE","nomeestacao":"Torreão","ultimovalor":0,"datahoraUltimovalor":"21/10/26 15:45",
                         "acc1hr":"-","acc3hr":"-","acc6hr":"-","acc12hr":"-","acc24hr":"-","acc48hr":"-",
                         "acc72hr":"-","acc96hr":"-","tipoestacao":1}"""),
                // estação [APAC] gravada sem município: casa pelo nome
                panel("""
                        {"cidade":"RECIFE","nomeestacao":"Nova Descoberta","ultimovalor":0,"datahoraUltimovalor":"22/10/26 00:20",
                         "acc1hr":"-","acc3hr":0.79,"acc6hr":0.99,"acc12hr":0.99,"acc24hr":2.96,"acc48hr":2.96,
                         "acc72hr":2.96,"acc96hr":2.96,"tipoestacao":1}"""),
                panel("""
                        {"cidade":"CARUARU","nomeestacao":"Centro","ultimovalor":5,"datahoraUltimovalor":"22/10/26 00:20",
                         "acc1hr":5,"acc3hr":5,"acc6hr":5,"acc12hr":5,"acc24hr":5,"acc48hr":5,"acc72hr":5,"acc96hr":5,
                         "tipoestacao":1}""")));
        sums = List.of(
                new StationRainSum("261160610A", "[CEMADEN] Alto Mandu", "RECIFE", -8.019, -34.931, 4.7),
                new StationRainSum("261160605A", "[APAC] Nova Descoberta", "Não informada", null, null, 3.0));

        List<CemadenAccumulationDTO> out = service.rmr();

        assertThat(out).extracting(CemadenAccumulationDTO::name).containsExactly("Alto Mandu", "Torreão", "Nova Descoberta");
        CemadenAccumulationDTO alto = out.get(0);
        assertThat(alto.stationId()).isEqualTo("261160610A");
        assertThat(alto.lat()).isEqualTo(-8.019);
        assertThat(alto.lastReadingUtc()).isEqualTo(Instant.parse("2026-10-22T00:20:00Z"));
        assertThat(alto.acc24hMm()).isEqualTo(4.73);
        assertThat(alto.mapiAcc24hMm()).isEqualTo(4.7);
        assertThat(alto.diff24hMm()).isEqualTo(-0.03);

        CemadenAccumulationDTO torreao = out.get(1);
        assertThat(torreao.stationId()).isNull();
        assertThat(torreao.acc6hMm()).isNull();
        assertThat(torreao.acc12hMm()).isEqualTo(0.0);
        assertThat(torreao.diff24hMm()).isNull();

        assertThat(out.get(2).stationId()).isEqualTo("261160605A");
        assertThat(out.get(2).acc1hMm()).isEqualTo(0.0);
    }

    @Test
    void reaproveitaOPainelEFalhaVira503() {
        when(client.fetchState("PE")).thenReturn(List.of());
        service.rmr();
        service.rmr();
        verify(client, times(1)).fetchState("PE");

        CemadenAccumulationClient down = mock(CemadenAccumulationClient.class);
        when(down.fetchState("PE")).thenThrow(new SourceUnavailableException("timeout"));
        CemadenAccumulationServiceImpl other = new CemadenAccumulationServiceImpl(down, observations, new AppProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(other::rmr).isInstanceOf(ServiceUnavailableException.class);
    }
}
