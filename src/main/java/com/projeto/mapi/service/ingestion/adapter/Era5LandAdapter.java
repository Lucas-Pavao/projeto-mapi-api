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
import java.util.List;
import java.util.function.Consumer;

/**
 * Umidade do solo 0-7 cm do ERA5-Land (Open-Meteo archive, {@code models=era5_land}). Mesmo desenho de
 * {@link OpenMeteoArchiveAdapter}: um registro de origem por (coordenada solicitada x hora), fuso UTC declarado
 * ({@code timezone=GMT} explícito, {@code utc_offset_seconds=0} conferido).
 *
 * <p>Fonte (consultada em 2026-10-10): https://open-meteo.com/en/docs/historical-weather-api - variável
 * {@code soil_moisture_0_to_7cm}, "m³/m³", ERA5-Land horária, 1950 até hoje, "Daily with 5 days delay".
 * É estado instantâneo (INSTANT/LAST). Latência de ~5 dias: a feature 22 não existe em produção (A.2.3).
 */
@Component
@RequiredArgsConstructor
public class Era5LandAdapter implements SourceAdapter {

    public static final String SOURCE = "OPEN_METEO_ERA5LAND";
    public static final String API_VARIABLE = "soil_moisture_0_to_7cm";
    public static final String VARIABLE = "soil_moisture_0_7cm";
    private static final int CHUNK_DAYS = 366;

    private final Era5LandClient client;
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
        return "era5-land/1";
    }

    @Override
    public void fetch(Instant from, Instant to, Consumer<FetchedBatch> sink) {
        LocalDate firstDay = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate lastDay = to.minusNanos(1).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate lastAvailable = clock.instant().atZone(ZoneOffset.UTC).toLocalDate()
                .minusDays(props.getEra5LandLagDays());
        if (lastDay.isAfter(lastAvailable)) lastDay = lastAvailable;

        for (FloodPoint p : floodPoints.findAll()) {
            for (LocalDate s = firstDay; !s.isAfter(lastDay); s = s.plusDays(CHUNK_DAYS)) {
                LocalDate e = s.plusDays(CHUNK_DAYS - 1L).isAfter(lastDay) ? lastDay : s.plusDays(CHUNK_DAYS - 1L);
                sink.accept(fetchChunk(p, s, e, from, to));
            }
        }
    }

    private FetchedBatch fetchChunk(FloodPoint p, LocalDate start, LocalDate end, Instant from, Instant to) {
        Era5LandClient.Response resp = client.fetch(p.getLatitude(), p.getLongitude(), start, end);
        JsonNode root = parse(resp.body());
        if (root.path("utc_offset_seconds").asInt(-1) != 0) {
            throw new SourceUnavailableException("ERA5-Land devolveu utc_offset_seconds="
                    + root.path("utc_offset_seconds") + " (esperado 0): fuso não é UTC");
        }
        JsonNode hourly = root.path("hourly");
        JsonNode times = hourly.path("time");
        if (!times.isArray()) throw new SourceUnavailableException("ERA5-Land sem hourly.time");
        JsonNode values = hourly.path(API_VARIABLE);
        String unit = root.path("hourly_units").path(API_VARIABLE).asText(null);

        String station = OpenMeteoArchiveAdapter.stationId(p.getLatitude(), p.getLongitude());
        Instant collectedAt = clock.instant();
        List<RawRecord> records = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            String label = times.get(i).asText();
            String norm = TimestampNormalizer.normalize(label);
            Instant ts = TimestampNormalizer.toInstant(norm, "UTC");
            if (ts.isBefore(from) || !ts.isBefore(to)) continue; // só registros de origem em [from, to)
            ObjectNode payload = mapper.createObjectNode();
            payload.put("time", label);
            payload.putObject("units").put(API_VARIABLE, unit);
            payload.set(API_VARIABLE, values.isArray() && i < values.size() ? values.get(i) : mapper.nullNode());
            records.add(new RawRecord(SOURCE, station, collectedAt, label, norm, declaredTz(), payload));
        }

        ObjectNode attrs = mapper.createObjectNode();
        attrs.put("grid_lat", root.path("latitude").asDouble());
        attrs.put("grid_lon", root.path("longitude").asDouble());
        attrs.put("elevation", root.path("elevation").asDouble());
        attrs.put("point_slug", p.getSlug());
        attrs.put("model", Era5LandHttpClient.MODEL);
        StationInfo info = new StationInfo(SOURCE, station, p.getName(), p.getLatitude(), p.getLongitude(),
                p.getMunicipality(), attrs);

        ObjectNode req = mapper.createObjectNode();
        req.put("url", resp.url());
        req.put("sha256", Hashing.sha256(resp.body()));
        req.put("timezone", "GMT");
        req.put("model", Era5LandHttpClient.MODEL);
        req.put("utc_offset_seconds", 0);
        return new FetchedBatch(records, List.of(info), req);
    }

    @Override
    public List<DraftValue> extract(RawRecord record) {
        JsonNode v = record.payload().path(API_VARIABLE);
        if (!v.isNumber()) return List.of();
        JsonNode unit = record.payload().path("units").path(API_VARIABLE);
        return List.of(new DraftValue(VARIABLE, "PT1H", v.asDouble(),
                unit.isMissingNode() || unit.isNull() ? null : unit.asText(), null));
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new SourceUnavailableException("ERA5-Land: JSON inválido", e);
        }
    }
}
