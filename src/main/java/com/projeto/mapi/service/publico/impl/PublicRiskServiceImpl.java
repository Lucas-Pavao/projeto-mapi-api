package com.projeto.mapi.service.publico.impl;

import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.dto.ModelValidationStatus;
import com.projeto.mapi.dto.PublicRiskDTO;
import com.projeto.mapi.dto.RiskLevel;
import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.exception.UnprocessableEntityException;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.flood.FloodPredictionHistoryService;
import com.projeto.mapi.service.publico.PublicRiskService;
import com.projeto.mapi.service.publico.RiskMeaning;
import com.projeto.mapi.service.sensor.SensorService;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PublicRiskServiceImpl implements PublicRiskService {

    static final double LAT_MIN = -8.4, LAT_MAX = -7.8, LON_MIN = -35.2, LON_MAX = -34.8;
    static final double POINT_RADIUS_KM = 1.0;
    /** Raio de busca do pluviômetro; o ADR 0013 não fixa um, usa-se o de 3 km dos demais vínculos do sistema. */
    static final double RAIN_RADIUS_KM = 3.0;
    static final Duration CACHE_TTL = Duration.ofSeconds(60);
    static final int CACHE_MAX_CELLS = 2000;

    private final FloodPointRepository pointRepository;
    private final FloodPredictionHistoryService history;
    private final SensorService sensorService;
    private final Clock clock;

    private record Cached(Instant at, PublicRiskDTO value) {
    }

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @Override
    public PublicRiskDTO assess(double rawLat, double rawLon) {
        double lat = round3(rawLat), lon = round3(rawLon);
        if (!(lat >= LAT_MIN && lat <= LAT_MAX && lon >= LON_MIN && lon <= LON_MAX)) {
            throw new UnprocessableEntityException("Coordenada fora da Região Metropolitana do Recife");
        }
        String key = lat + "," + lon;
        Instant now = clock.instant();
        Cached hit = cache.get(key);
        if (hit != null && Duration.between(hit.at(), now).compareTo(CACHE_TTL) < 0) return hit.value();

        PublicRiskDTO value = compute(lat, lon);
        if (cache.size() >= CACHE_MAX_CELLS) cache.clear();
        cache.put(key, new Cached(now, value));
        return value;
    }

    private PublicRiskDTO compute(double lat, double lon) {
        Optional<FloodPoint> nearest = pointRepository.findByActiveTrue().stream()
                .filter(p -> km(lat, lon, p.getLatitude(), p.getLongitude()) <= POINT_RADIUS_KM)
                .min(Comparator.comparingDouble(p -> km(lat, lon, p.getLatitude(), p.getLongitude())));

        Rain rain = nearestRain(lat, lon);
        if (nearest.isEmpty()) {
            return new PublicRiskDTO(null, rain.mm(), rain.station(), ModelValidationStatus.NOT_VALIDATED, RiskMeaning.NO_POINT);
        }
        FloodPoint p = nearest.get();
        Optional<FloodPredictionResponseDTO> pred = history.findLatest(p.getId(), p.getLatitude(), p.getLongitude());
        RiskLevel level = pred.map(FloodPredictionResponseDTO::riskLevel).orElse(RiskLevel.UNKNOWN);
        int distanceM = (int) (Math.round(km(lat, lon, p.getLatitude(), p.getLongitude()) * 1000.0 / 50.0) * 50);
        var point = new PublicRiskDTO.NearestPoint(p.getSlug(), p.getName(), distanceM, level,
                pred.map(FloodPredictionResponseDTO::issuedAt).orElse(null),
                pred.map(FloodPredictionResponseDTO::validUntil).orElse(null),
                pred.map(FloodPredictionResponseDTO::degradationReason).orElse(null));
        return new PublicRiskDTO(point, rain.mm(), rain.station(),
                pred.map(FloodPredictionResponseDTO::modelValidationStatus).orElse(ModelValidationStatus.NOT_VALIDATED),
                RiskMeaning.of(level));
    }

    private record Rain(Double mm, String station) {
    }

    private Rain nearestRain(double lat, double lon) {
        Map<String, Double> rain = sensorService.rainLastHourBySensor();
        if (rain.isEmpty()) return new Rain(null, null);
        return sensorService.getAllLatestData().stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null && rain.containsKey(s.getSensorId()))
                .filter(s -> km(lat, lon, s.getLatitude(), s.getLongitude()) <= RAIN_RADIUS_KM)
                .min(Comparator.comparingDouble((SensorResponseDTO s) -> km(lat, lon, s.getLatitude(), s.getLongitude())))
                .map(s -> new Rain(rain.get(s.getSensorId()), s.getSensorId()))
                .orElse(new Rain(null, null));
    }

    private static double km(double lat1, double lon1, double lat2, double lon2) {
        return GeoUtils.calculateDistance(lat1, lon1, lat2, lon2);
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
