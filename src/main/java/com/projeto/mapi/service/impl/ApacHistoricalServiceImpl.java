package com.projeto.mapi.service.impl;

import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.service.ApacHistoricalService;
import com.projeto.mapi.util.ApacStationRegistry;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApacHistoricalServiceImpl implements ApacHistoricalService {

    private final SensorDataRepository sensorDataRepository;
    private final FloodPointRepository floodPointRepository;
    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://dados.apac.pe.gov.br:41120")
            .defaultHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36")
            .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            .build();

    private List<FloodPoint> floodPointsCache;

    @Override
    public void ingestFullStateRainfall(int year) {
        ingestHistoricalRainfall("TODOS", year);
    }

    @Override
    public void ingestHistoricalRainfall(String stationCode, int year) {
        log.info("Iniciando coleta histórica APAC no ano {}. Alvo: {}", year, stationCode);
        
        if (floodPointsCache == null) {
            floodPointsCache = floodPointRepository.findAll();
        }

        // Se o stationCode vier completo (ex: APAC-PLUVIO-261160615A), extraímos apenas o miolo
        String cleanCode = stationCode.replace("APAC-PLUVIO-", "").replace("APAC-METEO-", "");

        for (int month = 1; month <= 12; month++) {
            LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
            LocalDateTime end = start.plusMonths(1).minusDays(1);
            
            if (end.isAfter(LocalDateTime.now())) end = LocalDateTime.now();
            if (start.isAfter(end)) break;

            String startDateStr = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String endDateStr = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            try {
                processMonthlyReport(startDateStr, endDateStr, stationCode, cleanCode);
                Thread.sleep(500); // Respeito ao servidor
            } catch (Exception e) {
                log.error("Erro ao processar intervalo {} a {} na APAC: {}", startDateStr, endDateStr, e.getMessage());
            }
        }
    }

    private void processMonthlyReport(String start, String end, String fullSensorId, String cleanCode) {
        log.info(">>>> Buscando relatório mensal APAC: {} até {} | Alvo: {}", start, end, cleanCode);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("mesorregiao", "Todas");
        formData.add("microrregiao", "Todas");
        formData.add("municipio", "Todos");
        formData.add("bacia", "Todas");
        formData.add("tipoBoletim", "Diário");
        formData.add("dataInicial", start);
        formData.add("dataFinal", end);

        try {
            String response = restClient.post()
                    .uri("/boletins/historico-pluviometrico/diario.php")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                log.warn("!!!! Resposta vazia recebida da APAC para o intervalo {} a {}", start, end);
                return;
            }

            parseNewHtmlTableWithJsoup(response, fullSensorId, cleanCode);
        } catch (Exception e) {
            log.error("!!!! Erro crítico na comunicação com portal APAC: {}", e.getMessage());
        }
    }

    private void parseNewHtmlTableWithJsoup(String html, String targetFullId, String cleanCode) {
        Document doc = Jsoup.parse(html);
        Element table = doc.select("table").first();
        if (table == null) {
            log.warn("Nenhuma tabela encontrada no HTML da APAC.");
            return;
        }

        Elements rows = table.select("tr");
        if (rows.isEmpty()) return;

        List<SensorData> batch = new ArrayList<>();
        java.util.Map<String, Integer> colMap = new java.util.HashMap<>();
        int savedRecords = 0;

        // Processar cabeçalho (primeira linha com <th> ou <td> que contém "Código GMMC")
        Element headerRow = rows.stream()
                .filter(r -> r.text().contains("Código GMMC"))
                .findFirst()
                .orElse(null);

        if (headerRow == null) {
            log.warn("Cabeçalho da tabela APAC não encontrado.");
            return;
        }

        Elements headerCells = headerRow.select("th, td");
        for (int i = 0; i < headerCells.size(); i++) {
            String h = headerCells.get(i).text().toUpperCase().trim();
            if (h.contains("CÓDIGO GMMC")) colMap.put("GMMC", i);
            else if (h.contains("IDENTIFICADOR")) colMap.put("IDENTIFICADOR", i);
            else if (h.contains("MUNICÍPIO") || h.contains("MUNICIPIO")) colMap.put("MUNICIPIO", i);
            else if (h.contains("ESTAÇÃO") || h.contains("ESTACAO")) colMap.put("ESTACAO", i);
            else if (h.contains("LATITUDE")) colMap.put("LATITUDE", i);
            else if (h.contains("LONGITUDE")) colMap.put("LONGITUDE", i);
            else if (h.contains("ANO/MÊS") || h.contains("ANO/MES")) colMap.put("ANOMES", i);
            else if (h.matches("\\d{1,2}")) {
                String dayNum = h.length() == 1 ? "0" + h : h;
                colMap.put("DAY_" + dayNum, i);
            }
        }

        log.debug("Colunas detectadas: {}", colMap.keySet());

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < colMap.size() - 31 || row.text().contains("Código GMMC")) continue;

            try {
                String gmmc = cells.get(colMap.get("GMMC")).text().trim();
                String municipio = cells.get(colMap.get("MUNICIPIO")).text().trim();
                String estacao = cells.get(colMap.get("ESTACAO")).text().trim();
                String latStr = cells.get(colMap.get("LATITUDE")).text().trim();
                String lonStr = cells.get(colMap.get("LONGITUDE")).text().trim();
                String anoMes = cells.get(colMap.get("ANOMES")).text().trim();

                if (anoMes.isEmpty() || !anoMes.contains("/")) continue;
                String[] parts = anoMes.split("/");
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);

                Double lat = parseCoord(latStr);
                Double lon = parseCoord(lonStr);

                // PADRONIZAÇÃO: sensorId é APAC-PLUVIO- + Código GMMC
                String sensorId = "APAC-PLUVIO-" + gmmc;
                boolean isTarget = cleanCode.equals("TODOS") || gmmc.equalsIgnoreCase(cleanCode);

                if (!isTarget) continue;

                // Percorre os dias (01 a 31)
                for (int day = 1; day <= 31; day++) {
                    String dayKey = String.format("DAY_%02d", day);
                    if (colMap.containsKey(dayKey)) {
                        String rainVal = cells.get(colMap.get(dayKey)).text().trim().replace(",", ".");
                        if (!rainVal.isEmpty() && !rainVal.equals("-")) {
                            try {
                                double rain = Double.parseDouble(rainVal);
                                LocalDateTime ts = LocalDateTime.of(year, month, day, 0, 0);
                                
                                saveToBatch(batch, sensorId, ts, rain, estacao, municipio, gmmc, lat, lon);
                                savedRecords++;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Erro ao processar linha da APAC: {}", e.getMessage());
            }

            if (batch.size() >= 500) flushBatch(batch);
        }
        
        flushBatch(batch);
        log.info("---- Fim do processamento Jsoup: {} registros persistidos.", savedRecords);
    }

    private Double parseCoord(String val) {
        if (val == null || val.isBlank() || val.equals("-")) return null;
        try {
            return Double.parseDouble(val.replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }

    private final java.util.Set<String> updatedPoints = new java.util.HashSet<>();

    private void saveToBatch(List<SensorData> batch, String sensorId, LocalDateTime ts, double rain, String station, String city, String code, Double lat, Double lon) {
        Double finalLat = lat;
        Double finalLon = lon;

        // Fallback para coordenadas se vierem nulas do HTML
        if (finalLat == null || finalLon == null) {
            ApacStationRegistry.StationMetadata meta = ApacStationRegistry.getMetadata(code);
            if (meta == null) meta = ApacStationRegistry.findByName(station);
            if (meta != null) {
                finalLat = meta.getLatitude();
                finalLon = meta.getLongitude();
            }
        }

        // Vincular ao FloodPoint por proximidade (Raio 2km) ou de forma global
        if (finalLat != null && finalLon != null) {
            if (floodPointsCache == null) floodPointsCache = floodPointRepository.findAll();
            final Double currentLat = finalLat;
            final Double currentLon = finalLon;

            Optional<FloodPoint> nearPoint = floodPointsCache.stream()
                    .filter(fp -> GeoUtils.calculateDistance(currentLat, currentLon, fp.getLatitude(), fp.getLongitude()) < 2.0)
                    .findFirst();

            if (nearPoint.isPresent()) {
                FloodPoint fp = nearPoint.get();
                String mappingKey = fp.getSlug() + ":" + code;
                if (!updatedPoints.contains(mappingKey)) {
                    if (fp.getPluviometerStationId() == null || !fp.getPluviometerStationId().equals(sensorId)) {
                        log.info("---- Vinculando ponto {} à estação APAC {}", fp.getSlug(), code);
                        fp.setPluviometerStationId(sensorId);
                        floodPointRepository.save(fp);
                    }
                    updatedPoints.add(mappingKey);
                }
            }
        } else {
            // Sem coordenadas (Estação meteorológica), vincular a todos
            if (floodPointsCache == null) floodPointsCache = floodPointRepository.findAll();
            boolean updatedAny = false;
            for (FloodPoint fp : floodPointsCache) {
                if (fp.getWeatherStationIds() == null) {
                    fp.setWeatherStationIds(new java.util.HashSet<>());
                }
                if (!fp.getWeatherStationIds().contains(sensorId)) {
                    fp.getWeatherStationIds().add(sensorId);
                    floodPointRepository.save(fp);
                    updatedAny = true;
                }
            }
            if (updatedAny) {
                log.info("---- Estação meteorológica {} vinculada a todos os pontos de monitoramento.", sensorId);
            }
        }

        // Verificar duplicidade antes de adicionar ao lote
        if (sensorDataRepository.findBySensorIdAndTimestamp(sensorId, ts).isEmpty()) {
            batch.add(SensorData.builder()
                    .sensorId(sensorId)
                    .timestamp(ts)
                    .accumulatedPrecipitation(rain)
                    .value(rain) // Valor principal para compatibilidade
                    .unit("mm")
                    .source("APAC_PORTAL")
                    .stationName(station)
                    .municipality(city)
                    .code(code) // GMMC
                    .latitude(finalLat)
                    .longitude(finalLon)
                    .build());
        }
    }

    private void flushBatch(List<SensorData> batch) {
        if (!batch.isEmpty()) {
            try {
                sensorDataRepository.saveAll(batch);
            } catch (Exception e) {
                log.warn("Erro ao salvar lote de sensores APAC (provável duplicata): {}", e.getMessage());
            }
            batch.clear();
        }
    }
}
