package com.projeto.mapi.service.ingestion.adapter.tide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.service.ingestion.DraftValue;
import com.projeto.mapi.service.ingestion.FetchedBatch;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.StationInfo;
import com.projeto.mapi.service.ingestion.adapter.SourceAdapter;
import com.projeto.mapi.service.ingestion.validation.TimestampNormalizer;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base dos adaptadores da maré prevista (extremos preamar/baixa-mar do Porto do Recife, porto {@code pe02}).
 * Um registro de origem por extremo: estação {@code pe02}, instante = data + hora LOCAL da tábua como veio,
 * fuso declarado {@code UTC-03:00}; a conversão para UTC é da validação (ADR 0006 seção 4). A altura fica em m
 * acima do NR da DHN e é gravada com {@code vertical_reference = NR_DHN_pe02} (ADR 0009: não converter ao datum do MDT).
 */
public abstract class AbstractTideExtremesAdapter implements SourceAdapter {

    private final ObjectMapper mapper;
    private final Clock clock;

    protected AbstractTideExtremesAdapter(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public String declaredTz() {
        return TideConstants.DECLARED_TZ;
    }

    /** Resultado do download: extremos (qualquer ordem) e a descrição da requisição. */
    protected record Loaded(List<TideExtreme> extremes, ObjectNode requestInfo) {}

    /**
     * Carrega os extremos dos dias locais {@code [firstDay, lastDay]} (já com folga de 1 dia em cada ponta para o
     * vizinho da classificação); {@code [coreFirst, coreLast]} são os dias da janela pedida, sem a folga. Falha da fonte vira {@link com.projeto.mapi.exception.SourceUnavailableException}.
     */
    protected abstract Loaded load(LocalDate firstDay, LocalDate lastDay, LocalDate coreFirst, LocalDate coreLast);

    /** Metadados da estação que a fonte conhece (cabeçalho do porto). */
    protected ObjectNode stationAttrs() {
        ObjectNode a = mapper.createObjectNode();
        a.put("vertical_reference", TideConstants.VERTICAL_REFERENCE);
        a.put("institution", "DHN");
        a.put("card", "902");
        a.put("mean_level_m", 1.28);
        a.put("declared_timezone", TideConstants.DECLARED_TZ);
        return a;
    }

    @Override
    public void fetch(Instant from, Instant to, Consumer<FetchedBatch> sink) {
        LocalDate coreFirst = LocalDateTime.ofInstant(from, ZoneOffset.ofHours(-3)).toLocalDate();
        LocalDate coreLast = LocalDateTime.ofInstant(to.minusNanos(1), ZoneOffset.ofHours(-3)).toLocalDate();
        Loaded loaded = load(coreFirst.minusDays(1), coreLast.plusDays(1), coreFirst, coreLast);

        List<TideExtreme> sorted = new ArrayList<>(loaded.extremes());
        sorted.sort(Comparator.comparing(TideExtreme::date).thenComparing(TideExtreme::time));
        List<String> kinds = TideExtremeClassifier.classify(sorted);

        Instant collectedAt = clock.instant();
        List<RawRecord> records = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            TideExtreme e = sorted.get(i);
            String text = e.date() + " " + String.format("%02d:%02d", e.time().getHour(), e.time().getMinute());
            String norm = TimestampNormalizer.normalize(text);
            Instant ts = TimestampNormalizer.toInstant(norm, declaredTz());
            if (ts.isBefore(from) || !ts.isBefore(to)) continue; // só registros de origem em [from, to)
            ObjectNode p = mapper.createObjectNode();
            p.put("date", e.date().toString());
            p.put("time", String.format("%02d:%02d", e.time().getHour(), e.time().getMinute()));
            p.put("level", e.level());
            p.put("unit", "m");
            if (kinds.get(i) == null) p.putNull("kind");
            else p.put("kind", kinds.get(i));
            p.put("vertical_reference", TideConstants.VERTICAL_REFERENCE);
            records.add(new RawRecord(source(), TideConstants.STATION, collectedAt, text, norm, declaredTz(), p));
        }

        StationInfo info = new StationInfo(source(), TideConstants.STATION,
                TideConstants.HARBOR_NAME, -8.056667, -34.868333, "Recife", stationAttrs());
        sink.accept(new FetchedBatch(records, List.of(info), loaded.requestInfo()));
    }

    @Override
    public List<DraftValue> extract(RawRecord record) {
        JsonNode p = record.payload();
        if (!p.path("level").isNumber()) return List.of();
        JsonNode kind = p.path("kind");
        return List.of(new DraftValue(TideConstants.VARIABLE_EXTREME, "EVENT", p.get("level").asDouble(), "m",
                kind.isTextual() ? kind.asText() : null));
    }
}
