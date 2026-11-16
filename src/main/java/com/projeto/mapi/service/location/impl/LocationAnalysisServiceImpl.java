package com.projeto.mapi.service.location.impl;

import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.dto.LocationAnalysisDTO;
import com.projeto.mapi.dto.LocationAnalysisDTO.Candidate;
import com.projeto.mapi.dto.LocationAnalysisDTO.Rain;
import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.dto.WeatherResponseDTO;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.analise.PointRainRepository;
import com.projeto.mapi.repository.analise.PointRainRepository.HourRow;
import com.projeto.mapi.repository.analise.PointRainRepository.MonthCheckRow;
import com.projeto.mapi.repository.station.StationCanonicalRepository;
import com.projeto.mapi.repository.station.StationCanonicalRepository.CanonicalView;
import com.projeto.mapi.service.analise.PointRainComposer;
import com.projeto.mapi.service.analise.PointRainService;
import com.projeto.mapi.service.flood.FloodPredictionHistoryService;
import com.projeto.mapi.service.location.LocationAnalysisService;
import com.projeto.mapi.service.publico.impl.PublicRiskServiceImpl;
import com.projeto.mapi.service.sensor.RainSpatialCheck;
import com.projeto.mapi.repository.ingestion.ObservationReadRepository.HourlyRainRow;
import com.projeto.mapi.service.sensor.SensorFreshnessPolicy;
import com.projeto.mapi.service.sensor.SensorService;
import com.projeto.mapi.service.sensor.cemaden.impl.MeasuredRainAccumulationServiceImpl;
import com.projeto.mapi.service.station.impl.StationUnificationServiceImpl;
import com.projeto.mapi.service.terrain.LocalElevationService;
import com.projeto.mapi.service.tide.TideService;
import com.projeto.mapi.service.weather.WeatherService;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Análise de local. Tudo é calculado no CENTRO da célula de ~200 m (0,002 grau) que contém a coordenada; a resposta
 * ecoa a coordenada pedida. O cache de 10 min (despejo em {@code CacheConfig}) é por célula e por {@code at}.
 *
 * <p><b>inArea / isSea.</b> {@code inArea}: a coordenada está dentro do retângulo da RMR já usado pela avaliação
 * pública ({@link PublicRiskServiceImpl#LAT_MIN} a {@code LAT_MAX}, {@code LON_MIN} a {@code LON_MAX}). {@code isSea}:
 * dentro da área, FORA do MDT do PE3D (a grade de 5 m cobre a terra firme e o estuário) e com altitude do Open-Meteo
 * menor ou igual a 0 (o modelo de 90 m usa 0 no oceano). O sistema não tem linha de costa, então "a leste da costa"
 * é aproximado por esse critério; sem clima (Open-Meteo fora do ar) não se afirma mar. Fora da área o terreno é
 * {@code null} e a chuva é sempre estimada.
 *
 * <p><b>Chuva.</b> Candidatas = estações canônicas a até 5 km (valor da hora: PED, senão ao vivo, ADR 0023); válidas =
 * mês OK ou sem checagem (ADR 0022); hora com pico isolado ({@link RainSpatialCheck}, mesma regra da série horária) sai
 * do valor da candidata (fica NO_VALUE, motivo ISOLATED_PEAK) antes de escolher; vale o maior valor entre as válidas ({@link PointRainComposer}). Há válida sem
 * nenhum valor: GAP, valores nulos, nunca estimativa. Sem nenhuma válida: ESTIMATED, pelo Open-Meteo. A "hora" é a
 * última hora fechada até {@code at}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LocationAnalysisServiceImpl implements LocationAnalysisService {

    static final double CELL_DEG = 0.002;
    static final double RAIN_RADIUS_M = 5000;
    static final double RIVER_RADIUS_KM = 6.0;
    static final ZoneId TIDE_ZONE = ZoneId.of("America/Recife");

    private final StationCanonicalRepository canonicals;
    private final PointRainRepository rain;
    private final FloodPointRepository floodPoints;
    private final FloodPredictionHistoryService predictions;
    private final WeatherService weatherService;
    private final LocalElevationService elevation;
    private final TideService tideService;
    private final SensorService sensorService;
    private final CacheManager cacheManager;

    /** Chave do cache: célula de ~200 m e instante pedido. */
    public static String cellKey(double lat, double lon) {
        return Math.floor(lat / CELL_DEG) + "," + Math.floor(lon / CELL_DEG);
    }

    private static double center(double v) {
        return Math.round((Math.floor(v / CELL_DEG) + 0.5) * CELL_DEG * 1e6) / 1e6;
    }

    @Override
    public LocationAnalysisDTO analyze(double lat, double lon, Instant at) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new InvalidRequestException("lat deve estar entre -90 e 90 e lon entre -180 e 180");
        }
        // Cache manual (não @Cacheable) para devolver a coordenada PEDIDA mesmo com a entrada de outra coordenada da célula.
        Cache cache = cacheManager.getCache("locationAnalysis");
        String key = cellKey(lat, lon) + "|" + (at == null ? "now" : at);
        LocationAnalysisDTO hit = cache == null ? null : cache.get(key, LocationAnalysisDTO.class);
        if (hit == null) {
            hit = compute(lat, lon, at);
            if (cache != null) cache.put(key, hit);
        }
        return new LocationAnalysisDTO(lat, lon, hit.at(), hit.inArea(), hit.isSea(), hit.rain(), hit.nearestPoint(),
                hit.tide(), hit.river(), hit.terrain(), hit.weather(), hit.attribution());
    }

    @Override
    public Rain measuredRain(double lat, double lon, Instant at) {
        return rain(lat, lon, at != null ? at : Instant.now(), true, null);
    }

    private LocationAnalysisDTO compute(double lat, double lon, Instant at) {
        double cLat = center(lat), cLon = center(lon);
        Instant ref = at != null ? at : Instant.now();
        boolean inArea = cLat >= PublicRiskServiceImpl.LAT_MIN && cLat <= PublicRiskServiceImpl.LAT_MAX
                && cLon >= PublicRiskServiceImpl.LON_MIN && cLon <= PublicRiskServiceImpl.LON_MAX;

        WeatherResponseDTO weather = weather(cLat, cLon);
        OptionalDouble mdt = inArea ? elevation.mdtElevationAt(cLat, cLon) : OptionalDouble.empty();
        boolean isSea = inArea && mdt.isEmpty() && weather != null && weather.elevation() <= 0;
        LocationAnalysisDTO.Terrain terrain = mdt.isPresent()
                ? new LocationAnalysisDTO.Terrain(mdt.getAsDouble(), null) : null;

        return new LocationAnalysisDTO(cLat, cLon, ref, inArea, isSea,
                rain(cLat, cLon, ref, inArea, weather), nearestPoint(cLat, cLon), tide(cLat, cLon, ref),
                river(cLat, cLon), terrain, weatherBlock(weather), MeasuredRainAccumulationServiceImpl.ATTRIBUTION);
    }

    // ---------------------------------------------------------------- chuva

    private Rain rain(double lat, double lon, Instant ref, boolean inArea, WeatherResponseDTO weather) {
        if (!inArea) return estimated(weather, List.of());
        Instant hourEnd = ref.truncatedTo(ChronoUnit.HOURS);
        Instant monthStart = hourEnd.minusNanos(1).atZone(ZoneOffset.UTC).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant nextMonth = monthStart.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        Map<String, MonthCheckRow> checks = new HashMap<>();
        for (MonthCheckRow r : rain.monthChecks(PointRainService.SOURCE, PointRainService.RULE_VERSION, monthStart, nextMonth)) {
            checks.put(r.stationId(), r);
        }

        record Near(CanonicalView c, double distanceM, MonthCheckRow check, Map<Instant, Double> series) {}
        List<Near> near = new ArrayList<>();
        for (CanonicalView c : canonicals.canonicals()) {
            double d = GeoUtils.calculateDistance(lat, lon, c.lat(), c.lon()) * 1000.0;
            if (d > RAIN_RADIUS_M) continue;
            String pedId = aliasId(c, StationUnificationServiceImpl.PED);
            near.add(new Near(c, d, pedId == null ? null : checks.get(pedId), series(c, hourEnd)));
        }
        near.sort(Comparator.comparingDouble(Near::distanceM));
        java.util.Set<String> isolated = new java.util.HashSet<>();
        for (Near n : near) {
            if (isIsolatedPeak(n.c(), n.series().get(hourEnd), hourEnd)) {
                isolated.add(n.c().canonicalId());
                n.series().remove(hourEnd);
            }
        }

        List<PointRainComposer.Candidate> inputs = new ArrayList<>();
        for (Near n : near) {
            Double v = n.series().get(hourEnd);
            inputs.add(new PointRainComposer.Candidate(n.c().canonicalId(), n.distanceM(), valid(n.check()),
                    v == null ? Double.NaN : v, 0, Double.NaN));
        }
        PointRainComposer.Hour composed = PointRainComposer.compose(inputs);
        if (composed.nValid() == 0) return estimated(weather, toCandidates(near.stream().map(n -> n.c()).toList(),
                near.stream().map(Near::distanceM).toList(), near.stream().map(Near::check).toList(),
                near.stream().map(n -> n.series().get(hourEnd)).toList(), null, isolated));

        List<Candidate> candidates = toCandidates(near.stream().map(n -> n.c()).toList(),
                near.stream().map(Near::distanceM).toList(), near.stream().map(Near::check).toList(),
                near.stream().map(n -> n.series().get(hourEnd)).toList(), composed.stationId(), isolated);
        if (composed.stationId() == null) {
            return new Rain("GAP", null, null, null, null, candidates, composed.nValid());
        }
        Near used = near.stream().filter(n -> n.c().canonicalId().equals(composed.stationId())).findFirst().orElseThrow();
        return new Rain("MEASURED", round(composed.value()), sumHours(used.series(), hourEnd, 3),
                sumHours(used.series(), hourEnd, 24),
                new LocationAnalysisDTO.Station(used.c().canonicalId(), used.c().name(), Math.round(used.distanceM())),
                candidates, composed.nValid());
    }

    /** Pico isolado da hora (RainSpatialCheck): vizinhas = demais canônicas a até 6 km com valor na mesma hora. */
    private boolean isIsolatedPeak(CanonicalView c, Double value, Instant hourEnd) {
        if (value == null || value < RainSpatialCheck.SUSPECT_MM) return false;
        String src = StationUnificationServiceImpl.PED;
        HourlyRainRow self = new HourlyRainRow(src, c.canonicalId(), hourEnd, value, c.lat(), c.lon());
        List<HourlyRainRow> rows = new ArrayList<>();
        rows.add(self);
        for (CanonicalView o : canonicals.canonicals()) {
            if (o.canonicalId().equals(c.canonicalId())
                    || GeoUtils.calculateDistance(c.lat(), c.lon(), o.lat(), o.lon()) > RainSpatialCheck.RADIUS_KM) continue;
            Double v = series(o, hourEnd).get(hourEnd);
            if (v != null) rows.add(new HourlyRainRow(src, o.canonicalId(), hourEnd, v, o.lat(), o.lon()));
        }
        return RainSpatialCheck.isIsolatedPeak(self, rows);
    }

    private static boolean valid(MonthCheckRow check) {
        return check == null || "PASS".equals(check.status()) || "UNCHECKED".equals(check.status());
    }

    private static List<Candidate> toCandidates(List<CanonicalView> cs, List<Double> dist, List<MonthCheckRow> checks,
                                                List<Double> values, String usedId, java.util.Set<String> isolated) {
        List<Candidate> out = new ArrayList<>();
        for (int i = 0; i < cs.size(); i++) {
            CanonicalView c = cs.get(i);
            MonthCheckRow chk = checks.get(i);
            String status = chk == null ? "UNCHECKED" : "PASS".equals(chk.status()) ? "OK" : chk.status();
            Double v = values.get(i);
            List<String> reasons = new ArrayList<>(chk == null ? List.<String>of() : chk.reasons());
            if (isolated.contains(c.canonicalId())) reasons.add("ISOLATED_PEAK");
            String role = !valid(chk) ? "EXCLUDED" : v == null ? "NO_VALUE" : c.canonicalId().equals(usedId) ? "USED" : "LOWER";
            out.add(new Candidate(c.canonicalId(), c.name(), Math.round(dist.get(i)), v, status,
                    reasons, role, c.lat(), c.lon()));
        }
        return out;
    }

    /** Horas (fim -> mm) da canônica em (hourEnd-24h, hourEnd]: PED se houver na hora, senão ao vivo. */
    private Map<Instant, Double> series(CanonicalView c, Instant hourEnd) {
        Map<Instant, Double> out = new HashMap<>();
        for (String source : List.of(StationUnificationServiceImpl.PED, StationUnificationServiceImpl.LIVE)) {
            String id = aliasId(c, source);
            if (id == null) continue;
            for (HourRow h : rain.hourly(source, id, hourEnd.minus(24, ChronoUnit.HOURS), hourEnd)) {
                if (h.value() != null) out.putIfAbsent(h.hourEndUtc(), h.value());
            }
        }
        return out;
    }

    private static String aliasId(CanonicalView c, String source) {
        for (String a : c.aliases()) if (a.startsWith(source + ":")) return a.substring(source.length() + 1);
        return null;
    }

    private static Double sumHours(Map<Instant, Double> series, Instant lastEnd, int n) {
        double s = 0;
        for (int i = 0; i < n; i++) {
            Double v = series.get(lastEnd.minus(i, ChronoUnit.HOURS));
            if (v == null) return null;
            s += v;
        }
        return round(s);
    }

    /** Open-Meteo: chuva da hora corrente e somas das últimas 3 e 24 horas do histórico horário; nulo onde faltar. */
    private static Rain estimated(WeatherResponseDTO w, List<Candidate> candidates) {
        Double now = w != null && w.current() != null ? round(w.current().precipitation()) : null;
        List<Double> h = w != null && w.hourly() != null ? w.hourly().precipitation() : null;
        return new Rain("ESTIMATED", now, tail(h, 3), tail(h, 24), null, candidates, 0);
    }

    private static Double tail(List<Double> h, int n) {
        if (h == null || h.size() < n) return null;
        double s = 0;
        for (Double v : h.subList(h.size() - n, h.size())) {
            if (v == null) return null;
            s += v;
        }
        return round(s);
    }

    private static Double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ---------------------------------------------------------------- demais blocos

    private LocationAnalysisDTO.NearestPoint nearestPoint(double lat, double lon) {
        FloodPoint p = floodPoints.findByActiveTrue().stream()
                .filter(x -> x.getLatitude() != null && x.getLongitude() != null)
                .min(Comparator.comparingDouble(x -> GeoUtils.calculateDistance(lat, lon, x.getLatitude(), x.getLongitude())))
                .orElse(null);
        if (p == null) return null;
        double km = Math.round(GeoUtils.calculateDistance(lat, lon, p.getLatitude(), p.getLongitude()) * 100.0) / 100.0;
        FloodPredictionResponseDTO last = predictions.findLatest(p.getId(), p.getLatitude(), p.getLongitude()).orElse(null);
        if (last == null || last.riskLevel() == null) return new LocationAnalysisDTO.NearestPoint(p.getSlug(), p.getName(), km, "UNKNOWN", null, null);
        return new LocationAnalysisDTO.NearestPoint(p.getSlug(), p.getName(), km, last.riskLevel().name(),
                last.floodProbability(), last.degradationReason() == null ? null : last.degradationReason().name());
    }

    private LocationAnalysisDTO.Tide tide(double lat, double lon, Instant ref) {
        try {
            LocalDateTime t = LocalDateTime.ofInstant(ref, TIDE_ZONE);
            Double level = tideService.getStoredTideHeightAt(lat, lon, t);
            if (level == null) return null;
            Double next = tideService.getStoredTideHeightAt(lat, lon, t.plusMinutes(10));
            return new LocationAnalysisDTO.Tide(round(level), next == null ? null : next > level, "Porto do Recife", "NR DHN");
        } catch (RuntimeException e) {
            log.warn("Maré indisponível para a análise de local: {}", e.getMessage());
            return null;
        }
    }

    private LocationAnalysisDTO.River river(double lat, double lon) {
        SensorResponseDTO best = null;
        double bestKm = Double.MAX_VALUE;
        for (SensorResponseDTO s : sensorService.getLatestWithFreshness("RIVER_LEVEL")) {
            if (s.getLatitude() == null || s.getLongitude() == null
                    || !"APAC".equals(SensorFreshnessPolicy.canonicalSource(s.getSource(), s.getSensorId()))) continue;
            double km = GeoUtils.calculateDistance(lat, lon, s.getLatitude(), s.getLongitude());
            if (km <= RIVER_RADIUS_KM && km < bestKm) {
                best = s;
                bestKm = km;
            }
        }
        if (best == null) return null;
        return new LocationAnalysisDTO.River(best.getSensorId(), best.getStationName(), best.getValue(),
                Math.round(bestKm * 100.0) / 100.0, true);
    }

    private WeatherResponseDTO weather(double lat, double lon) {
        try {
            return weatherService.getWeatherData(lat, lon);
        } catch (RuntimeException e) {
            log.warn("Open-Meteo indisponível para a análise de local: {}", e.getMessage());
            return null;
        }
    }

    private static LocationAnalysisDTO.Weather weatherBlock(WeatherResponseDTO w) {
        if (w == null || w.current() == null) return null;
        return new LocationAnalysisDTO.Weather(w.current().temperature(), w.current().humidity(), w.current().windSpeed(),
                "OPEN_METEO");
    }
}
