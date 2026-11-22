package com.projeto.mapi.service.warning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.service.ingestion.Hashing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parser puro da previsão do INMET por município ({@code /previsao/{ibge}}, prompt dos horizontes, bloco A).
 * Formato conferido em 2026-11-22: {@code {"<ibge>": {"dd/MM/yyyy": {"manha": {...}, "tarde": {...}, "noite": {...}}
 * | {...campos do dia...}}}}. Os 2 primeiros dias vêm por turno; os seguintes, um bloco só (período {@code DIA}).
 * Datas civis no horário de Brasília (fonte declarada {@code America/Sao_Paulo}). Sem quantidade de chuva na origem:
 * qualquer campo com "chuva", "precip" ou "prob" no nome vai para {@code rainFields}, como veio.
 */
public final class InmetForecastParser {

    public static final String SOURCE = "INMET_PREVISAO";
    public static final String SOURCE_TZ = InmetWarningParser.SOURCE_TZ;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, String> PERIODS = Map.of("manha", "MANHA", "tarde", "TARDE", "noite", "NOITE");
    /**
     * Fora do hash da entrada: as imagens (base64), que mudam de bytes sem mudar de previsão, e o campo "hora", que
     * acompanha a hora local da consulta ("13" às 13h56, "14" às 14h15 de 22/11/2026, mesma previsão) e não é emissão.
     */
    private static final Set<String> NOT_HASHED = Set.of("icone", "temp_max_tende_icone", "temp_min_tende_icone", "hora");

    private InmetForecastParser() {}

    public record ParsedForecast(
            String ibge,
            String municipality,
            String uf,
            LocalDate forecastDate,
            String period,
            String summary,
            String weather,
            String iconCode,
            BigDecimal tempMax,
            BigDecimal tempMin,
            BigDecimal humidityMax,
            BigDecimal humidityMin,
            String windDir,
            String windIntensity,
            String tempMaxTrend,
            String tempMinTrend,
            String issueHourText,
            String rainFieldsJson,
            String entryHash
    ) {}

    /** Lê a resposta de um município. Lança {@link IllegalStateException} se o formato não for o conferido. */
    public static List<ParsedForecast> parse(String json, String expectedIbge, ObjectMapper mapper) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Previsão do INMET ilegível: " + e.getMessage(), e);
        }
        JsonNode mun = root.path(expectedIbge);
        if (!mun.isObject() || mun.isEmpty()) {
            throw new IllegalStateException("Previsão do INMET sem o município " + expectedIbge);
        }
        List<ParsedForecast> out = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> days = mun.fields();
        while (days.hasNext()) {
            Map.Entry<String, JsonNode> day = days.next();
            LocalDate date;
            try {
                date = LocalDate.parse(day.getKey(), DATE_FMT);
            } catch (Exception e) {
                throw new IllegalStateException("Data fora do formato dd/MM/yyyy na previsão do INMET: " + day.getKey());
            }
            JsonNode v = day.getValue();
            boolean byPeriod = PERIODS.keySet().stream().anyMatch(v::has);
            if (byPeriod) {
                for (Map.Entry<String, String> p : PERIODS.entrySet()) {
                    if (v.has(p.getKey())) out.add(entry(expectedIbge, date, p.getValue(), v.get(p.getKey()), mapper));
                }
            } else {
                out.add(entry(expectedIbge, date, "DIA", v, mapper));
            }
        }
        out.sort((a, b) -> a.forecastDate().equals(b.forecastDate())
                ? Integer.compare(order(a.period()), order(b.period())) : a.forecastDate().compareTo(b.forecastDate()));
        return out;
    }

    private static int order(String period) {
        return switch (period) {
            case "MANHA" -> 0;
            case "TARDE" -> 1;
            case "NOITE" -> 2;
            default -> 3;
        };
    }

    private static ParsedForecast entry(String ibge, LocalDate date, String period, JsonNode n, ObjectMapper mapper) {
        ObjectNode forHash = mapper.createObjectNode();
        ObjectNode rain = mapper.createObjectNode();
        n.fields().forEachRemaining(f -> {
            if (NOT_HASHED.contains(f.getKey())) return;
            forHash.set(f.getKey(), f.getValue());
            String k = f.getKey().toLowerCase(Locale.ROOT);
            if (k.contains("chuva") || k.contains("precip") || k.contains("prob")) rain.set(f.getKey(), f.getValue());
        });
        forHash.put("_data", date.toString());
        forHash.put("_periodo", period);
        return new ParsedForecast(ibge, text(n, "entidade"), text(n, "uf"), date, period, text(n, "resumo"),
                text(n, "tempo"), text(n, "cod_icone"), num(n, "temp_max"), num(n, "temp_min"), num(n, "umidade_max"),
                num(n, "umidade_min"), text(n, "dir_vento"), text(n, "int_vento"), text(n, "temp_max_tende"),
                text(n, "temp_min_tende"), text(n, "hora"), rain.toString(), Hashing.contentHash(forHash));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    /** Número vindo como texto ("34"); vazio ou não numérico vira ausente, nunca zero. */
    private static BigDecimal num(JsonNode n, String field) {
        String s = text(n, field);
        if (s == null) return null;
        try {
            return new BigDecimal(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
