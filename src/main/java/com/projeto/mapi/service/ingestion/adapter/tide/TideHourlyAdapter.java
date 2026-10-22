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
import com.projeto.mapi.repository.ingestion.ObservationReadRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Série horária da maré prevista: valor nas horas cheias UTC, interpolado por cosseno entre os extremos
 * consecutivos já consolidados em {@code observation} ({@code tide_height}, EVENT) das fontes
 * {@link TabuaMareApiAdapter} e {@link DhnTabuaCsvAdapter}. Fonte derivada {@code TIDE_PE02_HOURLY}: passa pelo mesmo
 * pipeline (bruto com hash, validação de faixa, upsert versionado), então o payload de cada hora carrega os dois
 * extremos usados e muda de hash se a tábua de origem for corrigida. Rode DEPOIS de carregar os extremos.
 *
 * <p>Método e incerteza em {@link CosineTideInterpolator}. Intervalo entre extremos acima de
 * {@value #MAX_GAP_HOURS} h não é interpolado (o semiperíodo semidiurno é ~6,2 h; limite {@code [sem fonte]}).
 */
@Component
public class TideHourlyAdapter implements SourceAdapter {

    public static final String SOURCE = "TIDE_PE02_HOURLY";
    static final int MAX_GAP_HOURS = 8;
    private static final Duration MARGIN = Duration.ofHours(12);
    private static final int BATCH_HOURS = 24 * 30;

    private final ObservationReadRepository reader;
    private final ObjectMapper mapper;
    private final Clock clock;

    public TideHourlyAdapter(ObservationReadRepository reader, ObjectMapper mapper, Clock clock) {
        this.reader = reader;
        this.mapper = mapper;
        this.clock = clock;
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
        return "tide-hourly-cosine/1";
    }

    @Override
    public void fetch(Instant from, Instant to, Consumer<FetchedBatch> sink) {
        List<CosineTideInterpolator.Point> extremes = loadExtremes(from.minus(MARGIN), to.plus(MARGIN));
        List<CosineTideInterpolator.Hourly> hours =
                CosineTideInterpolator.hourly(extremes, from, to, Duration.ofHours(MAX_GAP_HOURS));
        StationInfo info = new StationInfo(SOURCE, TideConstants.STATION, TideConstants.HARBOR_NAME,
                -8.056667, -34.868333, "Recife", stationAttrs());
        Instant collectedAt = clock.instant();
        ObjectNode req = mapper.createObjectNode();
        req.put("origin", "observation (tide_height, EVENT) de TABUAMARE_PE02 e DHN_TABUA_PE02");
        req.put("method", "COSINE_BETWEEN_EXTREMES");
        req.put("max_gap_hours", MAX_GAP_HOURS);
        req.put("extremes_used", extremes.size());

        for (int i = 0; i < hours.size() || i == 0; i += BATCH_HOURS) {
            List<RawRecord> records = new ArrayList<>();
            for (CosineTideInterpolator.Hourly h : hours.subList(i, Math.min(hours.size(), i + BATCH_HOURS))) {
                String text = h.hour().toString();
                String norm = TimestampNormalizer.normalize(text);
                ObjectNode p = mapper.createObjectNode();
                p.put("hour_utc", text);
                p.put("level", Math.round(h.level() * 10_000.0) / 10_000.0);
                p.put("unit", "m");
                p.put("method", "COSINE_BETWEEN_EXTREMES");
                p.put("vertical_reference", TideConstants.VERTICAL_REFERENCE);
                p.set("before", point(h.before()));
                p.set("after", point(h.after()));
                records.add(new RawRecord(SOURCE, TideConstants.STATION, collectedAt, text, norm, declaredTz(), p));
            }
            sink.accept(new FetchedBatch(records, i == 0 ? List.of(info) : List.of(), req));
            if (hours.isEmpty()) break;
        }
    }

    private ObjectNode point(CosineTideInterpolator.Point p) {
        ObjectNode n = mapper.createObjectNode();
        n.put("ts_utc", p.ts().toString());
        n.put("level", p.level());
        n.put("source", p.source());
        return n;
    }

    private ObjectNode stationAttrs() {
        ObjectNode a = mapper.createObjectNode();
        a.put("vertical_reference", TideConstants.VERTICAL_REFERENCE);
        a.put("derived_from", "tide_height EVENT (TABUAMARE_PE02, DHN_TABUA_PE02)");
        a.put("method", "COSINE_BETWEEN_EXTREMES");
        a.put("uncertainty", "[sem fonte] não medida contra marégrafo; exata só para maré senoidal entre extremos");
        return a;
    }

    /** Extremos ACTIVE vigentes; se as duas fontes cobrem o mesmo instante, vale a da API. */
    private List<CosineTideInterpolator.Point> loadExtremes(Instant from, Instant to) {
        return reader.tideExtremes(from, to).stream()
                .map(e -> new CosineTideInterpolator.Point(e.tsUtc(), e.level(), e.source())).toList();
    }

    @Override
    public List<DraftValue> extract(RawRecord record) {
        JsonNode level = record.payload().path("level");
        if (!level.isNumber()) return List.of();
        return List.of(new DraftValue(TideConstants.VARIABLE_HOURLY, "PT1H", level.asDouble(), "m", "INTERP_COSSENO"));
    }
}
