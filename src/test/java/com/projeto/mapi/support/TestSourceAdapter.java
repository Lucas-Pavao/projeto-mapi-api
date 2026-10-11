package com.projeto.mapi.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.service.ingestion.DraftValue;
import com.projeto.mapi.service.ingestion.FetchedBatch;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.adapter.SourceAdapter;
import com.projeto.mapi.service.ingestion.validation.TimestampNormalizer;
import org.springframework.boot.test.context.TestComponent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Fonte falsa dos testes: entrega os lotes enfileirados pelo teste; payload = lista de leituras. */
@TestComponent
public class TestSourceAdapter implements SourceAdapter {

    public static final String SOURCE = "TEST_SRC";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static long clockTick = 0;

    private volatile List<List<RawRecord>> batches = new ArrayList<>();

    /** Uma leitura de um registro de origem. */
    public record Reading(String variable, String resolution, Double value, String unit) {}

    public static Reading r(String variable, String resolution, Double value) {
        return new Reading(variable, resolution, value, null);
    }

    /** Registro de origem; collectedAt cresce a cada chamada (ordem de "mais recente" determinística). */
    public static synchronized RawRecord rec(String station, String tsText, Reading... readings) {
        ObjectNode payload = MAPPER.createObjectNode();
        ArrayNode arr = payload.putArray("readings");
        for (Reading x : readings) {
            ObjectNode o = arr.addObject();
            o.put("variable", x.variable());
            o.put("resolution", x.resolution());
            if (x.value() == null) o.putNull("value"); else o.put("value", x.value());
            if (x.unit() != null) o.put("unit", x.unit());
        }
        return new RawRecord(SOURCE, station, Instant.parse("2026-10-10T00:00:00Z").plusSeconds(clockTick++),
                tsText, TimestampNormalizer.normalize(tsText), "UTC", payload);
    }

    public void setBatches(List<RawRecord>... lists) {
        this.batches = new ArrayList<>(List.of(lists));
    }

    public void reset() {
        batches = new ArrayList<>();
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public String declaredTz() {
        return "UTC";
    }

    @Override
    public String parserVersion() {
        return "test/1";
    }

    @Override
    public void fetch(Instant from, Instant to, Consumer<FetchedBatch> sink) {
        for (List<RawRecord> b : batches) sink.accept(new FetchedBatch(b, List.of(), MAPPER.createObjectNode()));
    }

    @Override
    public List<DraftValue> extract(RawRecord record) {
        List<DraftValue> out = new ArrayList<>();
        record.payload().path("readings").forEach(n -> out.add(new DraftValue(n.path("variable").asText(),
                n.path("resolution").asText(), n.get("value").isNull() ? null : n.get("value").asDouble(),
                n.has("unit") ? n.get("unit").asText() : null, null)));
        return out;
    }
}
