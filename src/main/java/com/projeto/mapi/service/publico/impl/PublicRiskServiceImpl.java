package com.projeto.mapi.service.publico.impl;

import com.projeto.mapi.dto.PublicRiskDTO;
import com.projeto.mapi.exception.UnprocessableEntityException;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.publico.PublicRiskService;
import com.projeto.mapi.service.publico.RiskMeaning;
import com.projeto.mapi.service.sensor.HourlyRainService;
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

    public static final double LAT_MIN = -8.4, LAT_MAX = -7.8, LON_MIN = -35.2, LON_MAX = -34.8;
    static final double POINT_RADIUS_KM = 1.0;
    /** Raio de busca do pluviômetro; o ADR 0013 não fixa um, usa-se o de 3 km dos demais vínculos do sistema. */
    static final double RAIN_RADIUS_KM = 3.0;
    static final Duration CACHE_TTL = Duration.ofSeconds(60);
    static final int CACHE_MAX_CELLS = 2000;

    private final FloodPointRepository pointRepository;
    private final com.projeto.mapi.service.alagamento.FloodForecastService floodForecast;
    private final HourlyRainService hourlyRain;
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
        boolean inTest = floodForecast.shadow();
        if (nearest.isEmpty()) {
            return new PublicRiskDTO(null, rain.mm(), rain.station(), true, inTest, RiskMeaning.NO_POINT);
        }
        FloodPoint p = nearest.get();
        int distanceM = (int) (Math.round(km(lat, lon, p.getLatitude(), p.getLongitude()) * 1000.0 / 50.0) * 50);
        if (inTest) {
            // Modo sombra: o nível não sai para o público; nunca vira "sem alerta".
            var point = new PublicRiskDTO.NearestPoint(p.getSlug(), p.getName(), distanceM, "SEM_DADO", null, null, "EM_TESTE",
                    p.getMunicipality());
            return new PublicRiskDTO(point, rain.mm(), rain.station(), true, true, RiskMeaning.IN_TEST);
        }
        var a = floodForecast.current().stream().filter(x -> x.slug().equals(p.getSlug())).findFirst().orElse(null);
        String level = a == null ? "SEM_DADO" : a.level();
        var point = new PublicRiskDTO.NearestPoint(p.getSlug(), p.getName(), distanceM, level, a == null ? null : a.worstHorizonHours(),
                a == null ? null : a.issuedAt(), a == null ? "SEM_CALCULO" : a.reason(), p.getMunicipality());
        return new PublicRiskDTO(point, rain.mm(), rain.station(), true, false, RiskMeaning.of(level));
    }

    private record Rain(Double mm, String station) {
    }

    /** Pluviômetro mais próximo (até 3 km) com hora fechada recente: soma horária de observation.precipitation. */
    private Rain nearestRain(double lat, double lon) {
        return hourlyRain.latestClosedHour().stream()
                .filter(r -> r.lat() != null && r.lon() != null && km(lat, lon, r.lat(), r.lon()) <= RAIN_RADIUS_KM)
                .min(Comparator.comparingDouble(r -> km(lat, lon, r.lat(), r.lon())))
                .map(r -> new Rain(r.mm(), r.stationId()))
                .orElse(new Rain(null, null));
    }

    private static double km(double lat1, double lon1, double lat2, double lon2) {
        return GeoUtils.calculateDistance(lat1, lon1, lat2, lon2);
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
