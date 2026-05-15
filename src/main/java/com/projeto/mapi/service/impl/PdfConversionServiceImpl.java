package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.TideTableResponseDTO;
import com.projeto.mapi.mapper.TideMapper;
import com.projeto.mapi.model.*;
import com.projeto.mapi.repository.TideTableRepository;
import com.projeto.mapi.service.PdfConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfConversionServiceImpl implements PdfConversionService {

    private final TideTableRepository tideTableRepository;

    private static final List<String> MONTHS = Arrays.asList(
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    );

    private static final List<String> WEEKDAYS_SHORT = Arrays.asList("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM", "SAB");
    private static final List<String> WEEKDAYS_LONG = Arrays.asList("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo", "sábado");

    private static final Map<String, String> STATE_MAPPING = Map.ofEntries(
            Map.entry("AMAPÁ", "AP"),
            Map.entry("PARÁ", "PA"),
            Map.entry("MARANHÃO", "MA"),
            Map.entry("PIAUÍ", "PI"),
            Map.entry("CEARÁ", "CE"),
            Map.entry("RIO GRANDE DO NORTE", "RN"),
            Map.entry("PARAÍBA", "PB"),
            Map.entry("PERNAMBUCO", "PE"),
            Map.entry("ALAGOAS", "AL"),
            Map.entry("SERGIPE", "SE"),
            Map.entry("BAHIA", "BA"),
            Map.entry("ESPÍRITO SANTO", "ES"),
            Map.entry("RIO DE JANEIRO", "RJ"),
            Map.entry("SÃO PAULO", "SP"),
            Map.entry("PARANÁ", "PR"),
            Map.entry("SANTA CATARINA", "SC"),
            Map.entry("RIO GRANDE DO SUL", "RS")
    );

    @Override
    @Transactional
    public TideTableResponseDTO convertAndSave(MultipartFile file, String state, Integer year) throws IOException {
        return convertAndSave(file.getBytes(), file.getOriginalFilename(), state, year);
    }

    @Override
    @Transactional
    public TideTableResponseDTO convertAndSave(byte[] pdfBytes, String filename, String state, Integer year) throws IOException {
        String text;
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false); 
            text = stripper.getText(document);
        }

        if (text == null || text.trim().isEmpty()) {
            throw new IOException("O PDF está vazio ou não pôde ser lido.");
        }

        TideTable tideTable = parseText(text, state, year);
        
        if (tideTable.getYear() == null) tideTable.setYear(year);
        if (tideTable.getState() == null) tideTable.setState(state);

        if (tideTable.getHarborName() != null && tideTable.getYear() != null) {
            List<TideTable> existingTables = tideTableRepository.findAllByHarborNameIgnoreCaseAndYear(tideTable.getHarborName(), tideTable.getYear());
            if (!existingTables.isEmpty()) {
                log.info("Removendo {} registro(s) duplicado(s) para {} em {}", existingTables.size(), tideTable.getHarborName(), tideTable.getYear());
                tideTableRepository.deleteAll(existingTables);
                tideTableRepository.flush(); 
            }
        }

        TideTable savedEntity = tideTableRepository.save(tideTable);
        return TideMapper.toDTO(savedEntity);
    }

    private TideTable parseText(String text, String providedState, Integer requestedYear) {
        String[] lines = text.split("\\r?\\n");
        TideTable tideTable = TideTable.builder()
                .state(providedState)
                .year(requestedYear)
                .geoLocations(new ArrayList<>())
                .months(new ArrayList<>())
                .build();

        MonthData currentMonth = null;
        DayData currentDay = null;
        boolean inMonthData = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (isMonthName(line)) {
                String monthName = MONTHS.stream().filter(m -> line.equalsIgnoreCase(m)).findFirst().get();
                currentMonth = tideTable.getMonths().stream()
                        .filter(m -> m.getMonthName().equalsIgnoreCase(monthName))
                        .findFirst()
                        .orElseGet(() -> {
                            MonthData m = MonthData.builder()
                                    .monthName(monthName)
                                    .month(MONTHS.indexOf(monthName) + 1)
                                    .tideTable(tideTable)
                                    .days(new ArrayList<>())
                                    .build();
                            tideTable.getMonths().add(m);
                            return m;
                        });
                currentDay = null;
                inMonthData = true;
                continue;
            }

            if (inMonthData && isPureNumeric(line) && line.length() <= 2) {
                if (i + 1 < lines.length) {
                    String nextLine = lines[i + 1].trim().toUpperCase();
                    if (WEEKDAYS_SHORT.contains(nextLine)) {
                        int dayNum = Integer.parseInt(line);
                        final int dNum = dayNum;
                        final MonthData fMonth = currentMonth;
                        
                        currentDay = fMonth.getDays().stream()
                                .filter(d -> d.getDay() == dNum)
                                .findFirst()
                                .orElseGet(() -> {
                                    DayData d = DayData.builder()
                                            .day(dNum)
                                            .weekdayName(WEEKDAYS_LONG.get(WEEKDAYS_SHORT.indexOf(nextLine)))
                                            .monthData(fMonth)
                                            .hours(new ArrayList<>())
                                            .build();
                                    fMonth.getDays().add(d);
                                    return d;
                                });
                        i++; 
                        continue;
                    }
                }
            }

            if (currentDay != null && line.matches("\\d{4}\\s+-?\\d+([,.]\\d+)?.*")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2 && isPureNumeric(parts[0])) {
                    String hourStr = parts[0];
                    String formattedHour = hourStr.substring(0, 2) + ":" + hourStr.substring(2) + ":00";
                    float level = Float.parseFloat(parts[1].replace(",", "."));
                    
                    final String fHour = formattedHour;
                    if (currentDay.getHours().stream().noneMatch(h -> h.getHour().equals(fHour))) {
                        currentDay.getHours().add(HourData.builder()
                                .hour(formattedHour)
                                .level(level)
                                .dayData(currentDay)
                                .build());
                    }
                    continue;
                }
            }

            if (line.contains("Latitude") && line.contains("Longitude")) {
                GeoLocation geo = parseGeoLocation(line, tideTable);
                if (tideTable.getGeoLocations().stream().noneMatch(g -> g.getDecimalLat().equals(geo.getDecimalLat()))) {
                    tideTable.getGeoLocations().add(geo);
                }
                
                if (line.contains("Fuso ")) {
                    tideTable.setTimezone(extractBetween(line, "Fuso ", " horas"));
                }
            } else if (line.contains("PORTO") || line.contains("ARQUIPÉLAGO") || line.contains(" - 20") || line.toUpperCase().contains("ESTADO DE")) {
                if (line.toUpperCase().contains("ESTADO DE")) {
                    String statePart = extractBetween(line.toUpperCase(), "ESTADO DE ", ")").trim();
                    if (STATE_MAPPING.containsKey(statePart)) {
                        tideTable.setState(STATE_MAPPING.get(statePart));
                    }
                } else if (line.toUpperCase().contains("FERNANDO DE NORONHA")) {
                    tideTable.setState("PE");
                }

                if (line.contains("-")) {
                    String[] parts = line.split("-");
                    if (tideTable.getHarborName() == null) {
                        tideTable.setHarborName(cleanHarborName(parts[0]));
                    }
                    try {
                        String yearStr = parts[parts.length - 1].trim();
                        if (yearStr.matches("\\d{4}") && (tideTable.getYear() == null || tideTable.getYear() < 2000)) {
                            tideTable.setYear(Integer.parseInt(yearStr));
                        }
                    } catch (Exception e) {}
                }
            } else if (line.contains("Nível Médio") && line.contains("Carta")) {
                try {
                    tideTable.setMeanLevel(Float.parseFloat(extractBetween(line, "Nível Médio ", " m").replace(",", ".")));
                } catch (Exception e) {}
                if (line.contains("Carta ")) {
                    tideTable.setCard(line.substring(line.indexOf("Carta ") + 6).trim());
                }
            }
        }

        return tideTable;
    }

    private boolean isMonthName(String name) {
        return MONTHS.stream().anyMatch(m -> m.equalsIgnoreCase(name));
    }

    private boolean isPureNumeric(String s) {
        return s != null && s.matches("\\d+");
    }

    private String cleanHarborName(String name) {
        String clean = name.replaceAll("^\\d+\\s+", "").trim();
        String upper = clean.toUpperCase();
        if (upper.startsWith("PORTO DO ")) clean = clean.substring(9);
        else if (upper.startsWith("PORTO DE ")) clean = clean.substring(9);
        else if (upper.startsWith("PORTO DA ")) clean = clean.substring(9);
        else if (upper.startsWith("ARQUIPÉLAGO DE ")) clean = clean.substring(15);
        
        if (clean.contains("(")) {
            clean = clean.substring(0, clean.indexOf("(")).trim();
        }
        return clean.trim();
    }

    private GeoLocation parseGeoLocation(String line, TideTable tideTable) {
        String latPart = extractBetween(line, "Latitude ", " Longitude");
        String lngPart = extractBetween(line, "Longitude ", " Fuso");
        return GeoLocation.builder()
                .tideTable(tideTable)
                .decimalLat(latPart)
                .decimalLng(lngPart)
                .lat(convertToDecimal(latPart))
                .lng(convertToDecimal(lngPart))
                .build();
    }

    private String convertToDecimal(String dms) {
        try {
            String clean = dms.replace("°", " ").replace("'", "").replace("\"", "").trim();
            String[] parts = clean.split("\\s+");
            if (parts.length >= 2) {
                double degrees = Double.parseDouble(parts[0]);
                double minutes = Double.parseDouble(parts[1]);
                String direction = parts[parts.length - 1].toUpperCase();
                
                double decimal = degrees + (minutes / 60.0);
                if (direction.equals("S") || direction.equals("W")) {
                    decimal = -decimal;
                }
                return String.format(java.util.Locale.US, "%.6f", decimal);
            }
        } catch (Exception e) {
            log.warn("Falha ao converter coordenada '{}' para decimal: {}", dms, e.getMessage());
        }
        return null;
    }

    private String extractBetween(String text, String start, String end) {
        int startIndex = text.indexOf(start);
        if (startIndex == -1) return "";
        startIndex += start.length();
        int endIndex = text.indexOf(end, startIndex);
        if (endIndex == -1) return text.substring(startIndex).trim();
        return text.substring(startIndex, endIndex).trim();
    }
}
