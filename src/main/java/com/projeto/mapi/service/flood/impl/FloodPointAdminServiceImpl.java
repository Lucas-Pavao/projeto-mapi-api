package com.projeto.mapi.service.flood.impl;

import com.projeto.mapi.dto.FloodPointPatchDTO;
import com.projeto.mapi.dto.FloodPointResponseDTO;
import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.dto.StationSuggestionDTO;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.exception.ResourceNotFoundException;
import com.projeto.mapi.mapper.FloodPointMapper;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.MapiService;
import com.projeto.mapi.service.flood.FloodPointAdminService;
import com.projeto.mapi.service.sensor.SensorFreshnessPolicy;
import com.projeto.mapi.service.sensor.SensorService;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FloodPointAdminServiceImpl implements FloodPointAdminService {

    private final FloodPointRepository pointRepository;
    private final FloodPointMapper pointMapper;
    private final MapiService mapiService;
    private final SensorService sensorService;

    @Override
    @CacheEvict(value = "floodPoints", allEntries = true)
    public FloodPointResponseDTO patch(String slug, FloodPointPatchDTO patch) {
        if (isEmpty(patch)) {
            throw new InvalidRequestException("Informe ao menos um campo para alterar");
        }
        FloodPoint point = pointRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Ponto não encontrado: " + slug));
        if (patch.name() != null && patch.name().isBlank()) {
            throw new InvalidRequestException("name não pode ser vazio");
        }
        pointMapper.applyPatch(patch, point);
        if (patch.description() != null && patch.description().isBlank()) {
            point.setDescription(null);
        }
        pointRepository.save(point);
        log.info("Ponto {} editado (campos: {})", slug, changedFields(patch));
        return mapiService.getFloodPointBySlug(slug);
    }

    @Override
    public List<StationSuggestionDTO> stationSuggestions(double lat, double lon, int radiusM) {
        return sensorService.getAllLatestData().stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                .map(s -> toSuggestion(s, lat, lon))
                .filter(s -> s.distanceM() <= radiusM)
                .sorted(Comparator.comparingInt(StationSuggestionDTO::distanceM))
                .toList();
    }

    private StationSuggestionDTO toSuggestion(SensorResponseDTO s, double lat, double lon) {
        int distanceM = (int) Math.round(GeoUtils.calculateDistance(lat, lon, s.getLatitude(), s.getLongitude()) * 1000.0);
        String type = s.getAccumulatedPrecipitation() != null ? "PRECIPITATION"
                : s.getWaterLevel() != null ? "RIVER_LEVEL" : s.getType();
        String source = SensorFreshnessPolicy.canonicalSource(s.getSource(), s.getSensorId());
        return new StationSuggestionDTO(s.getSensorId(),
                s.getStationName() != null ? s.getStationName() : s.getSensorId(),
                type, source == null ? "DESCONHECIDA" : source, distanceM,
                s.getTimestamp() == null ? null : s.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
    }

    private static boolean isEmpty(FloodPointPatchDTO p) {
        return p.name() == null && p.description() == null && p.latitude() == null && p.longitude() == null
                && p.active() == null && p.alertThresholdMm() == null && p.pluviometerStationIds() == null
                && p.riverLevelStationIds() == null && p.weatherStationIds() == null;
    }

    private static String changedFields(FloodPointPatchDTO p) {
        List<String> f = new java.util.ArrayList<>();
        if (p.name() != null) f.add("name");
        if (p.description() != null) f.add("description");
        if (p.latitude() != null) f.add("latitude");
        if (p.longitude() != null) f.add("longitude");
        if (p.active() != null) f.add("active");
        if (p.alertThresholdMm() != null) f.add("alertThresholdMm");
        if (p.pluviometerStationIds() != null) f.add("pluviometerStationIds");
        if (p.riverLevelStationIds() != null) f.add("riverLevelStationIds");
        if (p.weatherStationIds() != null) f.add("weatherStationIds");
        return String.join(",", f);
    }
}
