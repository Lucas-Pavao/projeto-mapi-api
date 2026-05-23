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
        log.info("TabuaMare: Buscando maré para Lat: {}, Lon: {}", latitude, longitude);
        try {
            String latLng = "[" + latitude + "," + longitude + "]";
            TabuaMareResponse<Object> nearestResponse = getNearestHarbor(latLng);
            
            log.info("TabuaMare: Resposta do porto mais próximo: {}", nearestResponse);

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
                
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                int month = now.getMonthValue();
                int day = now.getDayOfMonth();
                int hour = now.getHour();
                
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
                                    // Como a API só retorna picos (high/low), buscamos o evento mais próximo do horário atual
                                    return hours.stream()
                                        .min(Comparator.comparingInt(h -> {
                                            try {
                                                String hStr = h.get("hour").toString();
                                                int hVal = Integer.parseInt(hStr.split(":")[0]);
                                                return Math.abs(hVal - hour);
                                            } catch (Exception e) {
                                                return 24;
                                            }
                                        }))
                                        .map(h -> {
                                            Object levelObj = h.get("level");
                                            Double level = levelObj != null ? Double.parseDouble(levelObj.toString()) : null;
                                            log.info("TabuaMare: Maré encontrada (Evento mais próximo): {} - Nível: {}", h.get("hour"), level);
                                            return level;
                                        })
                                        .orElse(null);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("TabuaMare: Erro crítico ao processar maré para lat={}, lon={}: {}", latitude, longitude, e.getMessage(), e);
        }
        return null;
    }
}
