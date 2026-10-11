package com.projeto.mapi.service.ingestion.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.service.ingestion.FetchedBatch;
import com.projeto.mapi.service.sensor.apac.ApacEnvelopeMapper;
import com.projeto.mapi.service.sensor.apac.ApacScrapeClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fonte APAC_METEO24H (endpoint {@code meteorologia24h}): fuso UNVERIFIED (quarentena TZ_UNVERIFIED até
 * F2-DADOS-07). Mesma limitação do {@link CemadenAdapter}: janela rolante de ~24 h, sem consulta por intervalo;
 * a carga é o instantâneo da janela com todas as leituras. {@code [from, to)} não é aplicado (fuso não verificado).
 */
@Component
@RequiredArgsConstructor
public class ApacMeteo24hAdapter extends SensorEnvelopeAdapter {

    public static final String SOURCE = "APAC_METEO24H";

    private final ApacScrapeClient scrape;
    private final ApacEnvelopeMapper envelopes;
    private final AppProperties appProperties;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public String parserVersion() {
        return "apac-meteo24h/2";
    }

    @Override
    public void fetch(Instant from, Instant to, Consumer<FetchedBatch> sink) {
        String endpoint = appProperties.getApac().getMeteorologiaEndpoint();
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode item : scrape.fetchRawStations(endpoint)) {
            ObjectNode env = envelopes.meteorologia24h(item, "APAC/Meteorologia24h");
            if (env != null) out.add(env);
        }
        ObjectNode req = mapper.createObjectNode();
        req.put("kind", "SNAPSHOT_24H");
        req.put("endpoint", endpoint);
        req.put("records", out.size());
        sink.accept(snapshotBatch(out, clock.instant(), req));
    }
}
