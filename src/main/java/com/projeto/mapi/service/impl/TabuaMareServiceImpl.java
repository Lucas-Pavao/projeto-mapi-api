package com.projeto.mapi.service.impl;

import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.dto.TabuaMareResponse;
import com.projeto.mapi.service.TabuaMareService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

@Service
@Slf4j
public class TabuaMareServiceImpl implements TabuaMareService {

    private final RestClient restClient;

    public TabuaMareServiceImpl(RestClient.Builder restClientBuilder, AppProperties appProperties) {
        this.restClient = restClientBuilder
                .baseUrl(appProperties.getTabuamare().getApiUrl())
                .build();
    }

    @Override
    public TabuaMareResponse<List<String>> getStates() {
        return restClient.get()
                .uri("/states")
                .retrieve()
                .body(new ParameterizedTypeReference<TabuaMareResponse<List<String>>>() {});
    }

    @Override
    public TabuaMareResponse<List<Object>> getHarborNames(String state) {
        return restClient.get()
                .uri("/harbor_names/{state}", state)
                .retrieve()
                .body(new ParameterizedTypeReference<TabuaMareResponse<List<Object>>>() {});
    }

    @Override
    public TabuaMareResponse<List<Object>> getHarbors(String ids) {
        return restClient.get()
                .uri("/harbors/{ids}", ids)
                .retrieve()
                .body(new ParameterizedTypeReference<TabuaMareResponse<List<Object>>>() {});
    }

    @Override
    public TabuaMareResponse<List<Object>> getTideTable(String harbor, Integer month, String days) {
        return restClient.get()
                .uri("/tabua-mare/{harbor}/{month}/{days}", harbor, month, days)
                .retrieve()
                .body(new ParameterizedTypeReference<TabuaMareResponse<List<Object>>>() {});
    }

    @Override
    public TabuaMareResponse<Object> getNearestHarbor(String latLng) {
        return restClient.get()
                .uri("/nearest-harbor-independent-state/{latLng}", latLng)
                .retrieve()
                .body(new ParameterizedTypeReference<TabuaMareResponse<Object>>() {});
    }

    @Override
    public Double getCurrentTideHeight(double latitude, double longitude) {
        return getTideHeightAt(latitude, longitude, java.time.LocalDateTime.now());
    }

    @Override
    public Double getTideHeightAt(double latitude, double longitude, java.time.LocalDateTime timestamp) {
        log.info("TabuaMare: Buscando maré para Lat: {}, Lon: {} para a data: {}", latitude, longitude, timestamp);
        try {
            String latLng = "[" + latitude + "," + longitude + "]";
            TabuaMareResponse<Object> nearestResponse = getNearestHarbor(latLng);
            
            log.debug("TabuaMare: Resposta do porto mais próximo: {}", nearestResponse);

            if (nearestResponse != null && nearestResponse.getData() != null) {
                Map<String, Object> harborData = null;
                
                if (nearestResponse.getData() instanceof List) {
                    List<Map<String, Object>> list = (List<Map<String, Object>>) nearestResponse.getData();
                    if (!list.isEmpty()) {
                        harborData = list.get(0);
                    }
                } else if (nearestResponse.getData() instanceof Map) {
                    harborData = (Map<String, Object>) nearestResponse.getData();
                }

                if (harborData == null) {
                    log.warn("TabuaMare: Dados do porto não encontrados na resposta.");
                    return null;
                }

                Object idObj = harborData.get("id");
                Object stateObj = harborData.get("state");
                if (idObj == null) {
                    log.warn("TabuaMare: ID do porto não encontrado na resposta.");
                    return null;
                }
                
                String harborId = idObj.toString();
                if (harborId.matches("\\d+") && stateObj != null) {
                    String state = stateObj.toString().toLowerCase();
                    harborId = state + String.format("%02d", Integer.parseInt(harborId));
                }
                
                log.info("TabuaMare: Porto identificado: {}", harborId);
                
                int month = timestamp.getMonthValue();
                int day = timestamp.getDayOfMonth();
                int hour = timestamp.getHour();
                
                String days = "[" + day + "]";
                TabuaMareResponse<List<Object>> tideTable = getTideTable(harborId, month, days);
                
                if (tideTable != null && tideTable.getData() != null && !tideTable.getData().isEmpty()) {
                    Object firstData = tideTable.getData().get(0);
                    if (firstData instanceof Map) {
                        Map<String, Object> data = (Map<String, Object>) firstData;
                        List<Map<String, Object>> months = (List<Map<String, Object>>) data.get("months");
                        if (months != null && !months.isEmpty()) {
                            Map<String, Object> monthData = months.stream()
                                .filter(m -> m.get("month") != null && Integer.parseInt(m.get("month").toString()) == month)
                                .findFirst()
                                .orElse(months.get(0));

                            List<Map<String, Object>> daysList = (List<Map<String, Object>>) monthData.get("days");
                            if (daysList != null && !daysList.isEmpty()) {
                                Map<String, Object> dayData = daysList.stream()
                                    .filter(d -> d.get("day") != null && Integer.parseInt(d.get("day").toString()) == day)
                                    .findFirst()
                                    .orElse(daysList.get(0));

                                List<Map<String, Object>> hours = (List<Map<String, Object>>) dayData.get("hours");
                                if (hours != null && !hours.isEmpty()) {
                                    // Ordenar os eventos por horário
                                    List<Map<String, Object>> sortedHours = hours.stream()
                                        .sorted(Comparator.comparingInt(h -> {
                                            String hStr = h.get("hour").toString();
                                            return Integer.parseInt(hStr.split(":")[0]) * 60 + 
                                                   Integer.parseInt(hStr.split(":")[1]);
                                        }))
                                        .toList();

                                    int targetMinutes = hour * 60 + timestamp.getMinute();
                                    Map<String, Object> prev = null;
                                    Map<String, Object> next = null;

                                    for (Map<String, Object> h : sortedHours) {
                                        String hStr = h.get("hour").toString();
                                        int hMinutes = Integer.parseInt(hStr.split(":")[0]) * 60 + 
                                                       Integer.parseInt(hStr.split(":")[1]);
                                        
                                        if (hMinutes <= targetMinutes) {
                                            prev = h;
                                        } else {
                                            next = h;
                                            break;
                                        }
                                    }

                                    if (prev != null && next != null) {
                                        // Interpolação senoidal entre dois picos
                                        double h1 = Double.parseDouble(prev.get("level").toString());
                                        double h2 = Double.parseDouble(next.get("level").toString());
                                        
                                        String pStr = prev.get("hour").toString();
                                        int t1 = Integer.parseInt(pStr.split(":")[0]) * 60 + Integer.parseInt(pStr.split(":")[1]);
                                        
                                        String nStr = next.get("hour").toString();
                                        int t2 = Integer.parseInt(nStr.split(":")[0]) * 60 + Integer.parseInt(nStr.split(":")[1]);
                                        
                                        double fraction = (double) (targetMinutes - t1) / (t2 - t1);
                                        double interpolated = (h1 + h2) / 2.0 + (h1 - h2) / 2.0 * Math.cos(Math.PI * fraction);
                                        
                                        log.debug("TabuaMare: Interpolado entre {} ({}) e {} ({}) para {}: {}", 
                                            pStr, h1, nStr, h2, timestamp, interpolated);
                                        return interpolated;
                                    } else {
                                        // Se só temos um lado (início ou fim do dia), retorna o pico mais próximo
                                        Map<String, Object> nearest = sortedHours.stream()
                                            .min(Comparator.comparingInt(h -> {
                                                String hStr = h.get("hour").toString();
                                                int hMinutes = Integer.parseInt(hStr.split(":")[0]) * 60 + 
                                                               Integer.parseInt(hStr.split(":")[1]);
                                                return Math.abs(hMinutes - targetMinutes);
                                            })).get();
                                        return Double.parseDouble(nearest.get("level").toString());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("TabuaMare: Erro crítico ao processar maré para lat={}, lon={} em {}: {}", latitude, longitude, timestamp, e.getMessage());
        }
        return null;
    }
}
