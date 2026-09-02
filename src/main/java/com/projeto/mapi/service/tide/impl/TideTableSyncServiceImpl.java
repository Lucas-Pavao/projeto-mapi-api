package com.projeto.mapi.service.tide.impl;

import com.projeto.mapi.dto.TabuaMareResponse;
import com.projeto.mapi.dto.TideSyncSummaryDTO;
import com.projeto.mapi.model.DayData;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.GeoLocation;
import com.projeto.mapi.model.HourData;
import com.projeto.mapi.model.MonthData;
import com.projeto.mapi.model.TideTable;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.TideTableRepository;
import com.projeto.mapi.service.tide.TabuaMareService;
import com.projeto.mapi.service.tide.TideTableSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TideTableSyncServiceImpl implements TideTableSyncService {

    private static final long PACE_MS = 1200L;

    private final TabuaMareService tabuaMareService;
    private final TideTableRepository tideTableRepository;
    private final FloodPointRepository floodPointRepository;
    private final CacheManager cacheManager;

    @Override
    public TideSyncSummaryDTO syncYear(int year) {
        Map<String, Map<String, Object>> harborsById = discoverHarbors(floodPointRepository.findAll());

        int monthsSynced = 0;
        int errors = 0;
        List<String> harborNames = new ArrayList<>();

        for (Map.Entry<String, Map<String, Object>> entry : harborsById.entrySet()) {
            String harborId = entry.getKey();
            Map<String, Object> harborMeta = entry.getValue();
            try {
                TideTable table = buildTideTable(harborId, harborMeta, year);
                monthsSynced += table.getMonths() != null ? table.getMonths().size() : 0;
                replaceExisting(table);
                harborNames.add(table.getHarborName());
                log.info("Porto sincronizado: {} ({}) — {} meses.", table.getHarborName(), harborId,
                        table.getMonths() != null ? table.getMonths().size() : 0);
            } catch (Exception e) {
                errors++;
                log.error("Erro ao sincronizar porto {} ({}): {}", harborId, harborMeta.get("harbor_name"), e.getMessage());
            }
        }

        log.info("Sincronização de maré concluída: {} portos, {} meses, {} erros.", harborsById.size(), monthsSynced, errors);
        return new TideSyncSummaryDTO(harborsById.size(), monthsSynced, errors, harborNames);
    }

    @Override
    public TideSyncSummaryDTO syncCurrentYearIfMissing() {
        int year = LocalDate.now().getYear();
        if (!tideTableRepository.findAllByYear(year).isEmpty()) {
            log.debug("Tábua de maré local já populada para {}; nada a fazer.", year);
            return new TideSyncSummaryDTO(0, 0, 0, List.of());
        }
        return syncYear(year);
    }

    /**
     * Descobre o conjunto de portos relevantes calculando o "porto mais próximo" de cada ponto de
     * monitoramento. Arredonda a coordenada a ~11km antes de chamar a API para evitar repetir a
     * mesma busca para pontos vizinhos que quase sempre caem no mesmo porto.
     */
    private Map<String, Map<String, Object>> discoverHarbors(List<FloodPoint> points) {
        Map<String, Map<String, Object>> harbors = new LinkedHashMap<>();
        Set<String> seenBuckets = new HashSet<>();

        for (FloodPoint point : points) {
            if (point.getLatitude() == null || point.getLongitude() == null) continue;

            String bucket = round1(point.getLatitude()) + "," + round1(point.getLongitude());
            if (!seenBuckets.add(bucket)) continue;

            try {
                String latLng = "[" + point.getLatitude() + "," + point.getLongitude() + "]";
                Map<String, Object> harborData = extractHarborData(tabuaMareService.getNearestHarbor(latLng));
                if (harborData == null) continue;

                String harborId = resolveHarborId(harborData);
                if (harborId != null) {
                    harbors.putIfAbsent(harborId, harborData);
                }
            } catch (Exception e) {
                log.warn("Erro ao descobrir porto mais próximo do ponto {}: {}", point.getName(), e.getMessage());
            }
            sleep(PACE_MS);
        }
        return harbors;
    }

    private TideTable buildTideTable(String harborId, Map<String, Object> harborMeta, int year) {
        TideTable table = TideTable.builder()
                .year(year)
                .harborName(asString(harborMeta.get("harbor_name")))
                .state(asString(harborMeta.get("state")))
                .timezone(asString(harborMeta.get("timezone")))
                .card(asString(harborMeta.get("card")))
                .dataCollectionInstitution(asString(harborMeta.get("data_collection_institution")))
                .meanLevel(asFloat(harborMeta.get("mean_level")))
                .build();

        table.setGeoLocations(buildGeoLocations(table, harborMeta.get("geo_location")));

        List<MonthData> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            int daysInMonth = YearMonth.of(year, month).lengthOfMonth();
            String daysParam = buildDaysArray(daysInMonth);

            Map<String, Object> monthHarbor = fetchMonthWithRetry(harborId, month, daysParam);
            if (monthHarbor == null) {
                log.warn("Sem dados de maré para porto {} mês {}/{} após retentativas.", harborId, month, year);
                sleep(PACE_MS);
                continue;
            }

            Object monthsRaw = monthHarbor.get("months");
            if (monthsRaw instanceof List<?> monthsList && !monthsList.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> monthObj = (Map<String, Object>) monthsList.get(0);
                MonthData monthData = MonthData.builder()
                        .tideTable(table)
                        .month(asInteger(monthObj.get("month")))
                        .monthName(asString(monthObj.get("month_name")))
                        .build();
                monthData.setDays(buildDays(monthData, monthObj.get("days")));
                months.add(monthData);
            }
            sleep(PACE_MS);
        }
        table.setMonths(months);
        return table;
    }

    /**
     * A TabuaMare, sob rajada, às vezes devolve HTTP 200 com "months" vazio em vez de um erro
     * (validado ao vivo: 4 de 12 meses vieram vazios num sync real, mas o mesmo mês pedido
     * isoladamente depois retornou dados normalmente) — não é uma exceção, então Retry/CircuitBreaker
     * não pegam esse caso. getTideTable é @Cacheable, então uma simples nova chamada bateria no
     * cache e devolveria o mesmo vazio; evict explícito antes de cada retentativa.
     */
    private Map<String, Object> fetchMonthWithRetry(String harborId, int month, String daysParam) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            Map<String, Object> monthHarbor = extractHarborData(tabuaMareService.getTideTable(harborId, month, daysParam));
            boolean hasMonths = monthHarbor != null && monthHarbor.get("months") instanceof List<?> l && !l.isEmpty();
            if (hasMonths) return monthHarbor;

            if (attempt < 3) {
                log.warn("Resposta vazia da TabuaMare para porto {} mês {} (tentativa {}/3); retentando...", harborId, month, attempt);
                evictTideTableCache(harborId, month, daysParam);
                sleep(PACE_MS * 2);
            }
        }
        return null;
    }

    private void evictTideTableCache(String harborId, int month, String daysParam) {
        Cache cache = cacheManager.getCache("tideTableDaily");
        if (cache != null) {
            cache.evict(harborId + "-" + month + "-" + daysParam);
        }
    }

    private List<DayData> buildDays(MonthData monthData, Object daysRaw) {
        List<DayData> result = new ArrayList<>();
        if (!(daysRaw instanceof List<?> daysList)) return result;
        for (Object dayObj : daysList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> day = (Map<String, Object>) dayObj;
            DayData dayData = DayData.builder()
                    .monthData(monthData)
                    .day(asInteger(day.get("day")))
                    .weekdayName(asString(day.get("weekday_name")))
                    .build();
            dayData.setHours(buildHours(dayData, day.get("hours")));
            result.add(dayData);
        }
        return result;
    }

    private List<HourData> buildHours(DayData dayData, Object hoursRaw) {
        List<HourData> result = new ArrayList<>();
        if (!(hoursRaw instanceof List<?> hoursList)) return result;
        for (Object hourObj : hoursList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> hour = (Map<String, Object>) hourObj;
            result.add(HourData.builder()
                    .dayData(dayData)
                    .hour(asString(hour.get("hour")))
                    .level(asFloat(hour.get("level")))
                    .build());
        }
        return result;
    }

    private List<GeoLocation> buildGeoLocations(TideTable table, Object geoRaw) {
        List<GeoLocation> result = new ArrayList<>();
        if (!(geoRaw instanceof List<?> geoList)) return result;
        for (Object geoObj : geoList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> geo = (Map<String, Object>) geoObj;
            result.add(GeoLocation.builder()
                    .tideTable(table)
                    .lat(asString(geo.get("lat")))
                    .lng(asString(geo.get("lng")))
                    .decimalLat(asString(geo.get("decimal_lat")))
                    .decimalLng(asString(geo.get("decimal_lng")))
                    .latDirection(asString(geo.get("lat_direction")))
                    .lngDirection(asString(geo.get("lng_direction")))
                    .build());
        }
        return result;
    }

    private void replaceExisting(TideTable table) {
        List<TideTable> existing = tideTableRepository.findAllByHarborNameIgnoreCaseAndYear(table.getHarborName(), table.getYear());
        if (!existing.isEmpty()) {
            tideTableRepository.deleteAll(existing);
            tideTableRepository.flush();
        }
        tideTableRepository.save(table);
    }

    private Map<String, Object> extractHarborData(TabuaMareResponse<?> response) {
        if (response == null || response.getData() == null) return null;
        Object data = response.getData();
        if (data instanceof List<?> list) {
            if (list.isEmpty()) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> first = (Map<String, Object>) list.get(0);
            return first;
        }
        if (data instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            return map;
        }
        return null;
    }

    private String resolveHarborId(Map<String, Object> harborData) {
        Object idObj = harborData.get("id");
        if (idObj == null) return null;
        String harborId = idObj.toString();
        Object stateObj = harborData.get("state");
        if (harborId.matches("\\d+") && stateObj != null) {
            harborId = stateObj.toString().toLowerCase() + String.format("%02d", Integer.parseInt(harborId));
        }
        return harborId;
    }

    private String buildDaysArray(int daysInMonth) {
        StringBuilder sb = new StringBuilder("[");
        for (int d = 1; d <= daysInMonth; d++) {
            if (d > 1) sb.append(",");
            sb.append(d);
        }
        return sb.append("]").toString();
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private Integer asInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Float asFloat(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.floatValue();
        try {
            return Float.parseFloat(o.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
