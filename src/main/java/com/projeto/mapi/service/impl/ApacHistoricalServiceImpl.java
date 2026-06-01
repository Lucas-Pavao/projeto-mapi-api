package com.projeto.mapi.service.impl;

import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.service.ApacHistoricalService;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApacHistoricalServiceImpl implements ApacHistoricalService {

    private final SensorDataRepository sensorDataRepository;
    private final FloodPointRepository floodPointRepository;
    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://dados.apac.pe.gov.br:41120")
            .build();

    private List<FloodPoint> floodPointsCache;

    @Override
    public void ingestHistoricalRainfall(String stationCode, int year) {
        log.info("Iniciando coleta histórica APAC para o código {} no ano {}", stationCode, year);
        
        // Carrega cache de pontos se necessário
        if (floodPointsCache == null) {
            floodPointsCache = floodPointRepository.findAll();
        }

        // Se o stationCode vier completo (ex: APAC-PLUVIO-RECIFE-IBURA), extraímos apenas o miolo
        String cleanCode = stationCode.replace("APAC-PLUVIO-", "").replace("APAC-METEO-", "");

        for (int month = 1; month <= 12; month++) {
            LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
            LocalDateTime end = (month == 12) ? 
                    LocalDateTime.of(year, 12, 31, 23, 59) : 
                    start.plusMonths(1).minusDays(1);
            
            if (end.isAfter(LocalDateTime.now())) end = LocalDateTime.now();
            if (start.isAfter(end)) break;

            String startDateStr = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String endDateStr = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            try {
                processDateRange(startDateStr, endDateStr, stationCode, cleanCode);
                Thread.sleep(500); // Respeito ao servidor
            } catch (Exception e) {
                log.error("Erro ao processar intervalo {} a {} na APAC: {}", startDateStr, endDateStr, e.getMessage());
            }
        }
    }

    private void processDateRange(String start, String end, String fullSensorId, String cleanCode) {
        log.info(">>>> Iniciando busca APAC: {} até {} | Sensor Alvo: {}", start, end, fullSensorId);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("dataInicial", start);
        formData.add("dataFinal", end);
        formData.add("mesorregiao", "Todas");
        formData.add("microrregiao", "Todas");
        formData.add("municipio", "Todos");
        formData.add("bacia", "Todas");
        formData.add("tipoBoletim", "Diário");
        formData.add("download_excel", ""); 

        try {
            byte[] response = restClient.post()
                    .uri("/boletins/historico-pluviometrico/export_diario.php")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(byte[].class);

            if (response == null || response.length == 0) {
                log.warn("!!!! Resposta vazia recebida da APAC para o intervalo {} a {}", start, end);
                return;
            }

            // Verificar se é um XLSX real (PK...) ou HTML
            if (response[0] == 0x50 && response[1] == 0x4B) {
                log.info("---- Detectado formato BINÁRIO (XLSX) - Processando...");
                parseXlsx(response, fullSensorId, cleanCode);
            } else {
                log.info("---- Detectado formato TEXTO (HTML/Table) - Processando...");
                parseHtmlTable(new String(response), fullSensorId, cleanCode);
            }
        } catch (Exception e) {
            log.error("!!!! Erro crítico na comunicação com portal APAC: {}", e.getMessage());
        }
    }

    private void parseXlsx(byte[] data, String fullSensorId, String cleanCode) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<SensorData> batch = new ArrayList<>();
            
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                log.error("!!!! XLSX sem linha de cabeçalho.");
                return;
            }
            
            java.util.Map<String, Integer> colMap = mapHeaders(headerRow);
            log.info("---- Colunas mapeadas no XLSX: {}", colMap.keySet());
            
            int totalRows = sheet.getLastRowNum();
            int totalSaved = 0;

            for (int i = 1; i <= totalRows; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String municipio = getColValue(row, colMap, "MUNICÍPIO", -1);
                if (municipio.isEmpty()) municipio = getColValue(row, colMap, "MUNICIPIO", 3);
                
                String estacao = getColValue(row, colMap, "ESTAÇÃO", -1);
                if (estacao.isEmpty()) estacao = getColValue(row, colMap, "ESTACAO", 4);
                
                String codigo = getColValue(row, colMap, "CÓDIGO GMMC", -1);
                if (codigo.isEmpty()) codigo = getColValue(row, colMap, "GMMC", -1);
                if (codigo.isEmpty()) codigo = getColValue(row, colMap, "IDENTIFICADOR", -1);
                
                String anoMes = getColValue(row, colMap, "ANO/MÊS", -1);
                if (anoMes.isEmpty()) anoMes = getColValue(row, colMap, "ANO/MES", -1);

                String latStr = getColValue(row, colMap, "LATITUDE", -1);
                String lonStr = getColValue(row, colMap, "LONGITUDE", -1);
                Double lat = parseCoord(latStr);
                Double lon = parseCoord(lonStr);

                if (anoMes.isEmpty() || !anoMes.contains("/")) continue;

                String[] amParts = anoMes.split("/");
                int year = Integer.parseInt(amParts[0]);
                int month = Integer.parseInt(amParts[1]);

                // Como vamos salvar todos os sensores, geramos um ID padrão se não for o alvo
                String currentSensorId = isMatch(municipio, estacao, codigo, cleanCode) ? fullSensorId : "APAC-PLUVIO-" + codigo;

                // Extrair as chuvas diárias (colunas 01 a 31)
                for (int day = 1; day <= 31; day++) {
                    String dayKey = String.format("%02d", day);
                    Integer dayColIdx = colMap.get(dayKey);
                    
                    if (dayColIdx != null) {
                        Cell cell = row.getCell(dayColIdx);
                        String val = getCellValue(cell).trim();
                        
                        if (!val.isEmpty() && !val.equals("-")) {
                            try {
                                double rain = Double.parseDouble(val.replace(",", "."));
                                LocalDateTime ts = LocalDateTime.of(year, month, day, 0, 0);
                                
                                saveToBatch(batch, currentSensorId, ts, rain, estacao, municipio, codigo, lat, lon);
                                totalSaved++;
                            } catch (Exception ignored) {
                                // Ignora dias inválidos (ex: 31 de fevereiro) ou valores não numéricos
                            }
                        }
                    }
                }
            }
            log.info("---- Fim do processamento XLSX: {} linhas analisadas, extraídos {} registros diários no total.", totalRows, totalSaved);
            flushBatch(batch);
        } catch (Exception e) {
            log.error("!!!! Erro ao fazer parse do XLSX: {}", e.getMessage());
        }
    }

    private void parseHtmlTable(String html, String fullSensorId, String cleanCode) {
        Pattern rowPattern = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);
        Pattern cellPattern = Pattern.compile("<td.*?>(.*?)</td>", Pattern.DOTALL);
        
        Matcher rowMatcher = rowPattern.matcher(html);
        List<SensorData> batch = new ArrayList<>();
        java.util.Map<String, Integer> colMap = new java.util.HashMap<>();
        boolean headerFound = false;
        int rowCount = 0;
        int matches = 0;

        while (rowMatcher.find()) {
            rowCount++;
            String rowContent = rowMatcher.group(1);
            Matcher cellMatcher = cellPattern.matcher(rowContent);
            List<String> cells = new ArrayList<>();
            while (cellMatcher.find()) {
                cells.add(cellMatcher.group(1).trim().replaceAll("&nbsp;", "").replaceAll("<.*?>", ""));
            }
            
            if (!headerFound && !cells.isEmpty() && (cells.contains("MUNICIPIO") || cells.contains("ESTACAO") || cells.contains("Município"))) {
                for (int i = 0; i < cells.size(); i++) {
                    String h = cells.get(i).toUpperCase();
                    if (h.contains("MUNICIPIO") || h.contains("MUNICÍPIO")) colMap.put("MUNICIPIO", i);
                    else if (h.contains("ESTACAO") || h.contains("ESTAÇÃO")) colMap.put("ESTACAO", i);
                    else if (h.contains("DATA")) colMap.put("DATA", i);
                    else if (h.contains("CHUVA")) colMap.put("CHUVA", i);
                    else if (h.contains("CODIGO") || h.contains("CÓDIGO")) colMap.put("CODIGO", i);
                    else if (h.contains("GMMC")) colMap.put("GMMC", i);
                    else if (h.contains("LATITUDE")) colMap.put("LATITUDE", i);
                    else if (h.contains("LONGITUDE")) colMap.put("LONGITUDE", i);
                }
                headerFound = true;
                log.info("---- Cabeçalho HTML identificado: {}", colMap.keySet());
                continue;
            }

            if (cells.size() >= 6) {
                try {
                    String municipio = cells.get(colMap.getOrDefault("MUNICIPIO", 3));
                    String stationName = cells.get(colMap.getOrDefault("ESTACAO", 4));
                    String dateStr = cells.get(colMap.getOrDefault("DATA", 5));
                    String codigo = colMap.containsKey("CODIGO") ? cells.get(colMap.get("CODIGO")) : 
                                   (colMap.containsKey("GMMC") ? cells.get(colMap.get("GMMC")) : "");
                    
                    Double lat = colMap.containsKey("LATITUDE") ? parseCoord(cells.get(colMap.get("LATITUDE"))) : null;
                    Double lon = colMap.containsKey("LONGITUDE") ? parseCoord(cells.get(colMap.get("LONGITUDE"))) : null;

                    if (isMatch(municipio, stationName, codigo, cleanCode)) {
                        matches++;
                        String rainStr = cells.get(colMap.getOrDefault("CHUVA", 6)).replace(",", ".");
                        
                        if (dateStr.matches("\\d{2}/\\d{2}/\\d{4}")) {
                            String[] parts = dateStr.split("/");
                            LocalDateTime ts = LocalDateTime.of(
                                    Integer.parseInt(parts[2]), 
                                    Integer.parseInt(parts[1]), 
                                    Integer.parseInt(parts[0]), 0, 0);

                            double rain = Double.parseDouble(rainStr);
                            saveToBatch(batch, fullSensorId, ts, rain, stationName, municipio, codigo, lat, lon);
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (batch.size() >= 500) flushBatch(batch);
        }
        log.info("---- Fim do processamento HTML: {} linhas analisadas, {} correspondências encontradas para {}", rowCount, matches, cleanCode);
        flushBatch(batch);
    }

    private java.util.Map<String, Integer> mapHeaders(Row headerRow) {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        for (Cell cell : headerRow) {
            String h = getCellValue(cell).toUpperCase().trim();
            if (h.contains("MUNICIPIO") || h.contains("MUNICÍPIO")) map.put("MUNICIPIO", cell.getColumnIndex());
            else if (h.contains("ESTACAO") || h.contains("ESTAÇÃO")) map.put("ESTACAO", cell.getColumnIndex());
            else if (h.contains("CÓDIGO GMMC") || h.contains("CODIGO GMMC") || h.equals("GMMC")) map.put("GMMC", cell.getColumnIndex());
            else if (h.contains("IDENTIFICADOR")) map.put("IDENTIFICADOR", cell.getColumnIndex());
            else if (h.contains("ANO/MÊS") || h.contains("ANO/MES")) map.put("ANO/MÊS", cell.getColumnIndex());
            else if (h.matches("\\d{2}")) map.put(h, cell.getColumnIndex()); // Captura "01", "02", etc
            else if (h.contains("LATITUDE")) map.put("LATITUDE", cell.getColumnIndex());
            else if (h.contains("LONGITUDE")) map.put("LONGITUDE", cell.getColumnIndex());
            else if (h.contains("DATA")) map.put("DATA", cell.getColumnIndex());
            else if (h.contains("CHUVA")) map.put("CHUVA", cell.getColumnIndex());
        }
        return map;
    }

    private String getColValue(Row row, java.util.Map<String, Integer> map, String key, int defIdx) {
        Integer idx = map.get(key);
        if (idx == null) {
            if (defIdx == -1) return "";
            idx = defIdx;
        }
        return getCellValue(row.getCell(idx));
    }

    private boolean isMatch(String municipio, String estacao, String rowCode, String target) {
        if (target == null) return false;
        
        // 1. Tenta match por Código GMMC (Prioridade)
        if (rowCode != null && !rowCode.isBlank() && rowCode.equalsIgnoreCase(target)) {
            log.info("---- MATCH ENCONTRADO via GMMC: {} == {}", rowCode, target);
            return true;
        }

        // 2. Tenta match por Nome da Estação
        if (municipio == null || estacao == null) return false;
        String combined = (municipio + "-" + estacao).toUpperCase().replace(" ", "-");
        boolean match = combined.contains(target.toUpperCase()) || target.toUpperCase().contains(estacao.toUpperCase());
        
        if (match) {
            log.info("---- MATCH ENCONTRADO via Nome: {} -> {}", combined, target);
        } else {
            // Log detalhado para depuração (opcional, pode gerar muito log se o arquivo for gigante)
            // log.trace("Comparando: RowCode={} | Nome={} | Alvo={}", rowCode, combined, target);
        }
        
        return match;
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
        String finalSensorId = sensorId;

        // Se temos coordenadas, validamos proximidade com pontos de alagamento
        if (lat != null && lon != null) {
            if (floodPointsCache == null) {
                floodPointsCache = floodPointRepository.findAll();
            }

            Optional<FloodPoint> nearPoint = floodPointsCache.stream()
                    .filter(fp -> GeoUtils.calculateDistance(lat, lon, fp.getLatitude(), fp.getLongitude()) < 1.0) // Reduzido para 1km para maior precisão
                    .findFirst();

            if (nearPoint.isPresent()) {
                FloodPoint fp = nearPoint.get();
                finalSensorId = fp.getSlug(); // Mapeia para o slug do ponto para unificar histórico
                
                // Otimização: Só salva no banco se o mapeamento mudou e ainda não foi atualizado nesta sessão
                String mappingKey = fp.getSlug() + ":" + code;
                if (!updatedPoints.contains(mappingKey)) {
                    if (fp.getPluviometerStationId() == null || !fp.getPluviometerStationId().equals("APAC-PLUVIO-" + code)) {
                        log.info("---- Atualizando mapeamento do ponto {}: Estação APAC {} detectada.", fp.getSlug(), code);
                        fp.setPluviometerStationId("APAC-PLUVIO-" + code);
                        floodPointRepository.save(fp);
                    }
                    updatedPoints.add(mappingKey);
                }
            }
        }

        if (sensorDataRepository.findBySensorIdAndTimestamp(finalSensorId, ts).isEmpty()) {
            batch.add(SensorData.builder()
                    .sensorId(finalSensorId)
                    .timestamp(ts)
                    .accumulatedPrecipitation(rain)
                    .unit("mm")
                    .source("APAC_PORTAL")
                    .stationName(station)
                    .municipality(city)
                    .code(code)
                    .latitude(lat)
                    .longitude(lon)
                    .build());
        }
    }

    private void flushBatch(List<SensorData> batch) {
        if (!batch.isEmpty()) {
            sensorDataRepository.saveAll(batch);
            log.info("Persistidos {} registros APAC para sensor ID {}", batch.size(), batch.get(0).getSensorId());
            batch.clear();
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            default -> "";
        };
    }

    private LocalDateTime getCellDateValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            Date date = cell.getDateCellValue();
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        try {
            String val = getCellValue(cell);
            if (val.matches("\\d{2}/\\d{2}/\\d{4}")) {
                String[] parts = val.split("/");
                return LocalDateTime.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]), 0, 0);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Double getCellNumericValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        try {
            return Double.parseDouble(getCellValue(cell).replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }
}
