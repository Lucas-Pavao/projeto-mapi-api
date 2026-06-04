package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.*;
import com.projeto.mapi.model.FloodEvent;
import com.projeto.mapi.service.CivilDefenseService;
import com.projeto.mapi.service.FloodEventService;
import com.projeto.mapi.service.GeocodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CivilDefenseServiceImpl implements CivilDefenseService {

    private final FloodEventService floodEventService;
    private final GeocodingService geocodingService;
    private final RestClient restClient = RestClient.builder().baseUrl("https://dados.recife.pe.gov.br").build();

    @Override
    public void ingestFloodEvents(String resourceId) {
        log.info(">>> Iniciando ingestão via API: {}", resourceId);
        processSource(resourceId, null);
    }

    public void ingestFloodEventsViaCsv(String csvUrl) {
        log.info(">>> Iniciando ingestão via CSV: {}", csvUrl);
        processSource(null, csvUrl);
    }

    private void processSource(String resourceId, String csvUrl) {
        int[] results;
        if (resourceId != null) {
            results = processViaApi(resourceId);
        } else {
            results = processViaCsv(csvUrl);
        }
        log.info(">>> Fim Ingestão: {} analisados, {} salvos com sucesso.", results[0], results[1]);
    }

    private int[] processViaApi(String resourceId) {
        int offset = 0;
        int totalProcessed = 0;
        int totalSaved = 0;
        boolean hasMore = true;

        while (hasMore) {
            try {
                final int currentOffset = offset;
                CkanResponseDTO response = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/3/action/datastore_search")
                                .queryParam("resource_id", resourceId)
                                .queryParam("q", "alagamento alagado")
                                .queryParam("limit", 1000)
                                .queryParam("offset", currentOffset)
                                .build())
                        .retrieve()
                        .body(CkanResponseDTO.class);

                if (response == null || !response.getSuccess() || response.getResult() == null) break;

                java.util.List<CkanRecordDTO> records = response.getResult().getRecords();
                if (records.isEmpty()) break;

                for (CkanRecordDTO record : records) {
                    if (isRelevant(record)) {
                        if (saveRecord(record)) totalSaved++;
                    }
                    totalProcessed++;
                }

                offset += 1000;
                if (offset >= response.getResult().getTotal()) hasMore = false;
            } catch (Exception e) {
                log.error("Falha na API Datastore para {}: {}", resourceId, e.getMessage());
                hasMore = false;
            }
        }
        return new int[]{totalProcessed, totalSaved};
    }

    private int[] processViaCsv(String csvUrl) {
        int totalProcessed = 0;
        int totalSaved = 0;
        try {
            String csvContent = RestClient.create().get().uri(csvUrl).retrieve().body(String.class);
            if (csvContent == null || csvContent.isBlank()) return new int[]{0, 0};

            csvContent = csvContent.replace("\"\"\"", "\"").replace("\"\"", "\"");
            char delimiter = csvContent.contains(";") ? ';' : ',';

            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setDelimiter(delimiter)
                    .setQuote('\"')
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .setAllowMissingColumnNames(true)
                    .build();

            try (CSVParser parser = new CSVParser(new StringReader(csvContent), format)) {
                List<CSVRecord> records = parser.getRecords();
                if (records.isEmpty()) return new int[]{0, 0};

                Map<String, Integer> colMap = detectColumns(records.get(0));
                int start = isHeader(records.get(0)) ? 1 : 0;

                for (int i = start; i < records.size(); i++) {
                    CkanRecordDTO record = mapToDto(records.get(i), colMap);
                    if (isRelevant(record)) {
                        if (saveRecord(record)) totalSaved++;
                    }
                    totalProcessed++;
                }
            }
        } catch (Exception e) {
            log.error("Erro no processamento do CSV {}: {}", csvUrl, e.getMessage());
        }
        return new int[]{totalProcessed, totalSaved};
    }

    private Map<String, Integer> detectColumns(CSVRecord row) {
        Map<String, Integer> map = new java.util.HashMap<>();
        for (int i = 0; i < row.size(); i++) {
            String val = row.get(i).toLowerCase();
            if (val.contains("data")) map.put("data", i);
            if (val.contains("ocorrencia") || val.contains("ocorrência")) map.put("ocorrencia", i);
            if (val.contains("solicitacao") || val.contains("solicitação")) map.put("solicitacao", i);
            if (val.contains("endereco") || val.contains("logradouro")) map.put("endereco", i);
            if (val.contains("bairro")) map.put("bairro", i);
        }
        return map;
    }

    private boolean isHeader(CSVRecord row) {
        String first = row.get(0).toLowerCase();
        return first.contains("ano") || first.contains("data") || first.contains("regional") || first.contains("planície");
    }

    private CkanRecordDTO mapToDto(CSVRecord row, Map<String, Integer> colMap) {
        CkanRecordDTO dto = new CkanRecordDTO();
        if (!colMap.isEmpty()) {
            if (colMap.containsKey("data")) dto.setData(row.get(colMap.get("data")));
            if (colMap.containsKey("ocorrencia")) dto.setOcorrencia(row.get(colMap.get("ocorrencia")));
            if (colMap.containsKey("solicitacao")) dto.setSolicitacao(row.get(colMap.get("solicitacao")));
            if (colMap.containsKey("endereco")) dto.setEndereco(row.get(colMap.get("endereco")));
            if (colMap.containsKey("bairro")) dto.setBairro(row.get(colMap.get("bairro")));
        } else {
            dto.setData(safeGet(row, 2));
            dto.setOcorrencia(safeGet(row, 4));
            dto.setEndereco(safeGet(row, 5));
            dto.setBairro(safeGet(row, 7));
        }
        return dto;
    }

    private String safeGet(CSVRecord row, int... indices) {
        for (int idx : indices) {
            if (idx < row.size()) {
                String v = row.get(idx);
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }

    private boolean isRelevant(CkanRecordDTO record) {
        if (record == null) return false;
        String ocorrencia = record.getOcorrencia() != null ? record.getOcorrencia().toLowerCase() : "";
        String solicitacao = record.getSolicitacao() != null ? record.getSolicitacao().toLowerCase() : "";
        String text = ocorrencia + " " + solicitacao;
        return text.contains("alagado") || text.contains("alagamento") || text.contains("inunda");
    }

    private boolean saveRecord(CkanRecordDTO record) {
        String addr = record.getEndereco() != null ? record.getEndereco() : "Endereço N/A";
        
        // Se o endereço original já parece lixo (menos de 3 caracteres ou só símbolos), ignora silenciosamente
        if (addr.length() < 3 || addr.matches("^[\\W\\d]+$")) {
            return false;
        }

        try {
            Optional<ScraperEventDTO> eventDTO = convertToScraperEvent(record);
            if (eventDTO.isPresent()) {
                return floodEventService.ingestScraperEvent(eventDTO.get()) != null;
            } else {
                // Log apenas se for um endereço que parece real mas falhou na geocodificação
                if (addr.length() > 10) {
                    log.warn("[-] Geocodificação falhou para endereço potencialmente válido: {}", addr);
                }
            }
        } catch (Exception e) {
            log.warn("[-] Erro ao processar registro: {} - Motivo: {}", addr, e.getMessage());
        }
        return false;
    }

    @Override
    public void ingestLastYears(int years) {
        log.info("Sincronizando Defesa Civil (últimos {} anos)...", years);
        try {
            CkanPackageResponseDTO pkg = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/3/action/package_show")
                            .queryParam("id", "registro-de-atendimentos-da-defesa-civil").build())
                    .retrieve().body(CkanPackageResponseDTO.class);

            if (pkg == null || pkg.getResult() == null) return;

            int startYear = LocalDate.now().getYear() - years + 1;
            for (int y = LocalDate.now().getYear(); y >= startYear; y--) {
                final String yearStr = String.valueOf(y);
                pkg.getResult().getResources().stream()
                        .filter(r -> "CSV".equalsIgnoreCase(r.getFormat()))
                        .filter(r -> r.getName().contains("Atendimentos") && r.getName().contains(yearStr))
                        .forEach(r -> {
                            if (Boolean.TRUE.equals(r.getDatastoreActive())) ingestFloodEvents(r.getId());
                            else ingestFloodEventsViaCsv(r.getUrl());
                        });
            }
        } catch (Exception e) {
            log.error("Falha ao buscar catálogo: {}", e.getMessage());
        }
    }

    private Optional<ScraperEventDTO> convertToScraperEvent(CkanRecordDTO record) {
        String cleanAddr = sanitizeAddress(record.getEndereco());
        if (cleanAddr.isBlank()) return Optional.empty();

        Optional<double[]> coords = geocodingService.geocode(cleanAddr, record.getBairro(), "Recife");
        if (coords.isEmpty()) return Optional.empty();

        LocalDateTime dt = parseDate(record.getData());
        if (dt == null) return Optional.empty();

        return Optional.of(ScraperEventDTO.builder()
                .latitude(coords.get()[0]).longitude(coords.get()[1])
                .startTime(dt).severity(FloodEvent.Severity.MEDIUM)
                .description(record.getSolicitacao() + " - " + record.getEndereco())
                .source("DEFESA_CIVIL_RECIFE").build());
    }

    private LocalDateTime parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            if (s.contains("T")) return LocalDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME);
            if (s.contains(" ")) return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (Exception e) { return null; }
    }

    private String sanitizeAddress(String s) {
        if (s == null) return "";
        String clean = s.toLowerCase()
                .replace("nrte", "norte").replace("av.", "avenida").replace("r.", "rua")
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("(\\D+)(\\d+)$", "$1, $2")
                .replaceAll("[#\\^\\>\\$\\!\\=\\+\\?\\\"\\*\\_\\|\\/\\@\\&\\<\\%\\~\\{\\}\\[\\]\\`\\']+", " ")
                .trim().replaceAll("^[,\\-\\s]+|[,\\-\\s]+$", "");
        return clean.replaceAll("\\s{2,}", " ");
    }
}
