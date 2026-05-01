package com.projeto.mapi.service.impl;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfConversionServiceImpl implements PdfConversionService {

    private final TideTableRepository tideTableRepository;

    private static final List<String> MONTHS = Arrays.asList(
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    );

    private static final List<String> WEEKDAYS_SHORT = Arrays.asList("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM");
    private static final List<String> WEEKDAYS_LONG = Arrays.asList("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo");

    @Override
    @Transactional
    public TideTable convertAndSave(MultipartFile file, String state, Integer year) throws IOException {
        String text;
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            text = stripper.getText(document);
        }

        TideTable tideTable = parseText(text, state, year);
        return tideTableRepository.save(tideTable);
    }

    private TideTable parseText(String text, String state, Integer year) {
        String[] lines = text.split("\\r?\\n");
        TideTable tideTable = TideTable.builder()
                .state(state)
                .year(year)
                .geoLocations(new ArrayList<>())
                .months(new ArrayList<>())
                .build();

        GeoLocation currentGeo = null;
        MonthData currentMonth = null;
        DayData currentDay = null;
        boolean recentlyCapturedPageNumber = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (line.matches("\\d+")) {
                recentlyCapturedPageNumber = true;
                continue;
            }

            if (line.contains("HORA ALT(m)")) continue;

            if (recentlyCapturedPageNumber && (line.contains("- " + year) || line.chars().filter(ch -> ch == '-').count() >= 2)) {
                recentlyCapturedPageNumber = false;
                String[] parts = line.split("-");
                if (parts.length >= 2) {
                    StringBuilder harborName = new StringBuilder();
                    for (int j = 0; j < parts.length - 1; j++) {
                        harborName.append(parts[j].trim()).append(" ");
                    }
                    tideTable.setHarborName(harborName.toString().trim());
                }
                continue;
            }

            if (line.contains("Latitude") && line.contains("Longitude")) {
                currentGeo = parseGeoLocation(line, tideTable);
                tideTable.getGeoLocations().add(currentGeo);

                if (line.contains("Fuso ")) {
                    String timezone = extractBetween(line, "Fuso ", " horas");
                    tideTable.setTimezone(timezone);
                }
                continue;
            }

            if (line.contains("Nível Médio") && line.contains("Carta")) {
                String meanLevelStr = extractBetween(line, "Nível Médio ", " m").replace(",", ".");
                tideTable.setMeanLevel(Float.parseFloat(meanLevelStr));
                tideTable.setCard(line.substring(line.indexOf("Carta ") + 6).trim());
                tideTable.setDataCollectionInstitution(line.split(" ")[0]);
                continue;
            }

            if (MONTHS.contains(line)) {
                currentMonth = MonthData.builder()
                        .monthName(line)
                        .month(MONTHS.indexOf(line) + 1)
                        .tideTable(tideTable)
                        .days(new ArrayList<>())
                        .build();
                tideTable.getMonths().add(currentMonth);
                currentDay = null;
                continue;
            }

            if (currentMonth != null && line.matches("\\d+")) {
                if (i + 1 < lines.length) {
                    String nextLine = lines[i + 1].trim();
                    if (WEEKDAYS_SHORT.contains(nextLine)) {
                        currentDay = DayData.builder()
                                .day(Integer.parseInt(line))
                                .weekdayName(WEEKDAYS_LONG.get(WEEKDAYS_SHORT.indexOf(nextLine)))
                                .monthData(currentMonth)
                                .hours(new ArrayList<>())
                                .build();
                        currentMonth.getDays().add(currentDay);
                        i++;
                        continue;
                    }
                }
            }

            if (currentDay != null && line.matches("\\d{4}\\s+-?\\d+([,.]\\d+)?")) {
                String[] parts = line.split("\\s+");
                String hourStr = parts[0];
                String formattedHour = hourStr.substring(0, 2) + ":" + hourStr.substring(2) + ":00";
                float level = Float.parseFloat(parts[1].replace(",", "."));

                HourData hourData = HourData.builder()
                        .hour(formattedHour)
                        .level(level)
                        .dayData(currentDay)
                        .build();
                currentDay.getHours().add(hourData);
            }
        }

        return tideTable;
    }

    private GeoLocation parseGeoLocation(String line, TideTable tideTable) {
        String latPart = extractBetween(line, "Latitude ", " Longitude");
        String lngPart = extractBetween(line, "Longitude ", " Fuso");

        return GeoLocation.builder()
                .tideTable(tideTable)
                .decimalLat(latPart)
                .decimalLng(lngPart)
                .build();
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
