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
 * Fonte CEMADEN (pelo espelho da APAC, endpoint {@code cemaden}): fuso America/Recife (UTC-3), verificado em
 * F2-DADOS-07 pelo teste do relógio (V13).
 *
 * <p>O endpoint só devolve uma janela rolante de ~24 h (medido em 2026-10-10: 8.088 itens de 09/10 20:45 a 10/10
 * 20:40, 334 estações); não existe consulta por intervalo sem credencial. Por isso a "carga" é o instantâneo
 * completo da janela, com todas as leituras (o coletor ao vivo guarda só a última por estação). {@code [from, to)}
 * não é aplicado: o endpoint não aceita intervalo.
 */
@Component
@RequiredArgsConstructor
public class CemadenAdapter extends SensorEnvelopeAdapter {

    public static final String SOURCE = "CEMADEN";

    private final ApacScrapeClient scrape;
    private final ApacEnvelopeMapper envelopes;
    private final AppProperties appProperties;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Override
    public String source() {
        return SOURCE;
    }

    /** Fuso verificado em F2-DADOS-07 (teste B, UTC-3): o bruto novo já nasce com o fuso declarado. */
    @Override
    public String declaredTz() {
        return "America/Recife";
    }

    @Override
    public String parserVersion() {
        return "apac-cemaden/3";
    }

    @Override
    public void fetch(Instant from, Instant to, Consumer<FetchedBatch> sink) {
        String endpoint = appProperties.getApac().getCemadenEndpoint();
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode item : scrape.fetchRawStations(endpoint)) {
            ObjectNode env = envelopes.cemaden(item, "APAC/Cemaden");
            if (env != null) out.add(env);
        }
        ObjectNode req = mapper.createObjectNode();
        req.put("kind", "SNAPSHOT_24H");
        req.put("endpoint", endpoint);
        req.put("records", out.size());
        sink.accept(snapshotBatch(out, clock.instant(), req));
    }
}
