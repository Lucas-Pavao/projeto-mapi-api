package com.projeto.mapi.service.ingestion.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.exception.SourceUnavailableException;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.ingestion.DraftValue;
import com.projeto.mapi.service.ingestion.FetchedBatch;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.StationInfo;
import com.projeto.mapi.service.ingestion.validation.TimestampNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Open-Meteo archive (UTC confirmado). Um registro de origem por (coordenada solicitada x hora). A coordenada
 * real da grade vai em station.attrs. O fuso NÃO é convertido aqui: o adaptador declara UTC.
 */
@Component
@RequiredArgsConstructor
public class OpenMeteoArchiveAdapter implements SourceAdapter {

    public static final String SOURCE = "OPEN_METEO_ARCHIVE";
    private static final int CHUNK_DAYS = 366;

    /** variável da API -> variável do catálogo. */
    private static final Map<String, String> VARIABLES = new LinkedHashMap<>();

    static {
        VARIABLES.put("temperature_2m", "temperature");
        VARIABLES.put("relative_humidity_2m", "humidity");
        VARIABLES.put("surface_pressure", "pressure");
        VARIABLES.put("precipitation", "precipitation");
        VARIABLES.put("weather_code", "weather_code");
        VARIABLES.put("wind_speed_10m", "wind_speed");
        VARIABLES.put("shortwave_radiation", "solar_radiation");
    }

    private final OpenMeteoArchiveClient client;
    private final FloodPointRepository floodPoints;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final IngestionProperties props;

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
        return "open-meteo-archive/1";
    }

    public static String stationId(double lat, double lon) {
        return String.format(Locale.ROOT, "%.4f,%.4f", lat, lon);
    }

    @Override
    public void fetch(Instant from, Instant to, Consumer<FetchedBatch> sink) {
        LocalDate firstDay = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate lastDay = to.minusNanos(1).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate lastAvailable = clock.instant().atZone(ZoneOffset.UTC).toLocalDate()
                .minusDays(props.getOpenMeteoArchiveLagDays());
        if (lastDay.isAfter(lastAvailable)) lastDay = lastAvailable;

        for (FloodPoint p : floodPoints.findAll()) {
            for (LocalDate s = firstDay; !s.isAfter(lastDay); s = s.plusDays(CHUNK_DAYS)) {
                LocalDate e = s.plusDays(CHUNK_DAYS - 1L).isAfter(lastDay) ? lastDay : s.plusDays(CHUNK_DAYS - 1L);
                sink.accept(fetchChunk(p, s, e, from, to));
            }
        }
    }

    private FetchedBatch fetchChunk(FloodPoint p, LocalDate start, LocalDate end, Instant from, Instant to) {
        OpenMeteoArchiveClient.Response resp = client.fetch(p.getLatitude(), p.getLongitude(), start, end);
        JsonNode root = parse(resp.body());
        if (root.path("utc_offset_seconds").asInt(-1) != 0) {
            throw new SourceUnavailableException("Open-Meteo archive devolveu utc_offset_seconds="
                    + root.path("utc_offset_seconds") + " (esperado 0): fuso não é UTC");
        }
        JsonNode hourly = root.path("hourly");
        JsonNode units = root.path("hourly_units");
        JsonNode times = hourly.path("time");
        if (!times.isArray()) throw new SourceUnavailableException("Open-Meteo archive sem hourly.time");

        String station = stationId(p.getLatitude(), p.getLongitude());
        Instant collectedAt = clock.instant();
        List<RawRecord> records = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            String label = times.get(i).asText();
            String norm = TimestampNormalizer.normalize(label);
            Instant ts = TimestampNormalizer.toInstant(norm, "UTC");
            if (ts.isBefore(from) || !ts.isBefore(to)) continue; // só registros de origem em [from, to)
            ObjectNode payload = mapper.createObjectNode();
            payload.put("time", label);
            ObjectNode u = payload.putObject("units");
            for (String api : VARIABLES.keySet()) {
                JsonNode arr = hourly.path(api);
                payload.set(api, arr.isArray() && i < arr.size() ? arr.get(i) : mapper.nullNode());
                if (units.has(api)) u.put(api, units.get(api).asText());
            }
            records.add(new RawRecord(SOURCE, station, collectedAt, label, norm, declaredTz(), payload));
        }

        ObjectNode attrs = mapper.createObjectNode();
        attrs.put("grid_lat", root.path("latitude").asDouble());
        attrs.put("grid_lon", root.path("longitude").asDouble());
        attrs.put("elevation", root.path("elevation").asDouble());
        attrs.put("point_slug", p.getSlug());
        StationInfo info = new StationInfo(SOURCE, station, p.getName(), p.getLatitude(), p.getLongitude(),
                p.getMunicipality(), attrs);

        ObjectNode req = mapper.createObjectNode();
        req.put("url", resp.url());
        req.put("sha256", Hashing.sha256(resp.body()));
        req.put("timezone", "GMT");
        req.put("utc_offset_seconds", 0);
        return new FetchedBatch(records, List.of(info), req);
    }

    @Override
    public List<DraftValue> extract(RawRecord record) {
        JsonNode payload = record.payload();
        List<DraftValue> out = new ArrayList<>();
        VARIABLES.forEach((api, variable) -> {
            JsonNode v = payload.path(api);
            if (v.isNull() || v.isMissingNode() || !v.isNumber()) return;
            JsonNode unit = payload.path("units").path(api);
            out.add(new DraftValue(variable, "PT1H", v.asDouble(), unit.isMissingNode() ? null : unit.asText(), null));
        });
        return out;
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new SourceUnavailableException("Open-Meteo archive: JSON inválido", e);
        }
    }
}
