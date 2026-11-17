package com.projeto.mapi.service.warning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.service.ingestion.Hashing;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parser puro dos avisos meteorológicos do INMET ({@code /avisos/ativos}, ADR 0024).
 * Fuso declarado da origem: {@code America/Sao_Paulo} (horário oficial de Brasília).
 */
public final class InmetWarningParser {

    public static final String SOURCE = "INMET_AVISOS";
    public static final String SOURCE_TZ = "America/Sao_Paulo";
    public static final ZoneId ZONE = ZoneId.of(SOURCE_TZ);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Municípios da Região Metropolitana do Recife -> código IBGE de 7 dígitos. */
    private static final Map<String, String> RMR_IBGE = Map.ofEntries(
            Map.entry("RECIFE", "2611606"),
            Map.entry("JABOATAO DOS GUARARAPES", "2607901"),
            Map.entry("JABOATAO", "2607901"),
            Map.entry("OLINDA", "2609600"),
            Map.entry("PAULISTA", "2610707"),
            Map.entry("CAMARAGIBE", "2603454"),
            Map.entry("SAO LOURENCO DA MATA", "2613701"),
            Map.entry("ABREU E LIMA", "2600054"),
            Map.entry("IGARASSU", "2606804"),
            Map.entry("CABO DE SANTO AGOSTINHO", "2602902"),
            Map.entry("MORENO", "2609402"),
            Map.entry("IPOJUCA", "2607208"),
            Map.entry("ITAMARACA", "2607604"),
            Map.entry("ILHA DE ITAMARACA", "2607604"),
            Map.entry("ITAPISSUMA", "2607752"),
            Map.entry("ARACOIABA", "2601052")
    );

    private InmetWarningParser() {}

    public record WarningKey(int warningId, int sequenceId) {}

    public record ParsedWarning(
            int warningId,
            int sequenceId,
            Integer externalId,
            String capCode,
            String inicioText,
            Instant validFrom,
            Instant validTo,
            String severity,
            Integer severityId,
            String colorHex,
            String description,
            List<String> risks,
            List<String> instructions,
            List<String> geocodes,
            String municipalities,
            String polygonGeoJson,
            boolean altered,
            boolean closed,
            String rawHash,
            JsonNode rawPayload
    ) {
        public WarningKey key() {
            return new WarningKey(warningId, sequenceId);
        }

        public boolean touchesAny(Set<String> targetGeocodes) {
            if (targetGeocodes == null || targetGeocodes.isEmpty()) return true;
            for (String g : geocodes) {
                if (targetGeocodes.contains(g)) return true;
            }
            return false;
        }
    }

    public record ParseResult(int totalRawItems, List<ParsedWarning> warnings) {}

    /** Resolve nome de município ou código IBGE para o código de 7 dígitos quando conhecido. */
    public static Optional<String> resolveIbge(String municipalityOrCode) {
        if (municipalityOrCode == null || municipalityOrCode.isBlank()) return Optional.empty();
        String trimmed = municipalityOrCode.trim();
        if (trimmed.matches("\\d{7}")) return Optional.of(trimmed);
        String norm = normalize(trimmed);
        return Optional.ofNullable(RMR_IBGE.get(norm));
    }

    public static String normalize(String text) {
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return nfd.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** Faz o parse de {@code {"hoje": [...], "futuro": [...]}} deduplicando por (id_aviso, id_sequencia). */
    public static ParseResult parse(String json, ObjectMapper mapper) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON inválido do INMET: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject() || (!root.has("hoje") && !root.has("futuro"))) {
            throw new IllegalArgumentException("Resposta do INMET sem arrays 'hoje'/'futuro'");
        }
        Map<WarningKey, ParsedWarning> dedup = new LinkedHashMap<>();
        int total = 0;
        for (String bucket : List.of("hoje", "futuro")) {
            JsonNode arr = root.get(bucket);
            if (arr == null || !arr.isArray()) continue;
            for (JsonNode item : arr) {
                total++;
                ParsedWarning w = parseItem(item, mapper);
                dedup.putIfAbsent(w.key(), w);
            }
        }
        return new ParseResult(total, List.copyOf(dedup.values()));
    }

    static ParsedWarning parseItem(JsonNode item, ObjectMapper mapper) {
        int warningId = requiredInt(item, "id_aviso");
        int sequenceId = item.hasNonNull("id_sequencia") ? item.get("id_sequencia").asInt() : 1;
        Integer externalId = item.hasNonNull("id") ? item.get("id").asInt() : null;
        String capCode = textOrNull(item, "codigo");
        String inicioText = resolveTimestampText(item, "inicio", "data_inicio", "hora_inicio");
        String fimText = resolveTimestampText(item, "fim", "data_fim", "hora_fim");
        Instant validFrom = parseBrasilia(inicioText);
        Instant validTo = parseBrasilia(fimText);
        String severity = textOrNull(item, "severidade");
        if (severity == null || severity.isBlank()) severity = "Desconhecida";
        Integer severityId = item.hasNonNull("id_severidade") ? item.get("id_severidade").asInt() : null;
        String colorHex = textOrNull(item, "aviso_cor");
        String description = textOrNull(item, "descricao");
        List<String> risks = stringList(item.get("riscos"));
        List<String> instructions = stringList(item.get("instrucoes"));
        List<String> geocodes = parseGeocodes(textOrNull(item, "geocodes"));
        String municipalities = textOrNull(item, "municipios");
        String polygon = validJsonOrNull(textOrNull(item, "poligono"), mapper);
        boolean altered = boolValue(item.get("alterado"));
        boolean closed = boolValue(item.get("encerrado"));

        ObjectNode staged = item.isObject() ? ((ObjectNode) item).deepCopy() : mapper.createObjectNode();
        staged.remove("icone"); // PNG em base64 (~30-40 kB por item) omitido do staging
        String hash = Hashing.contentHash(staged);

        return new ParsedWarning(warningId, sequenceId, externalId, capCode, inicioText, validFrom, validTo,
                severity, severityId, colorHex, description, risks, instructions, geocodes, municipalities,
                polygon, altered, closed, hash, staged);
    }

    private static int requiredInt(JsonNode item, String field) {
        if (!item.hasNonNull(field)) {
            throw new IllegalArgumentException("Aviso do INMET sem campo obrigatório '" + field + "'");
        }
        return item.get(field).asInt();
    }

    private static String resolveTimestampText(JsonNode item, String directField, String dateField, String timeField) {
        String direct = textOrNull(item, directField);
        if (direct != null && !direct.isBlank()) return direct.trim();
        String d = textOrNull(item, dateField);
        String h = textOrNull(item, timeField);
        if (d != null && d.length() >= 10 && h != null && !h.isBlank()) {
            return d.substring(0, 10) + " " + h.trim();
        }
        throw new IllegalArgumentException("Aviso do INMET sem horário '" + directField + "'");
    }

    static Instant parseBrasilia(String text) {
        return LocalDateTime.parse(text.trim(), TS_FMT).atZone(ZONE).toInstant();
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static boolean boolValue(JsonNode v) {
        if (v == null || v.isNull()) return false;
        if (v.isBoolean()) return v.asBoolean();
        return "true".equalsIgnoreCase(v.asText().trim());
    }

    private static List<String> stringList(JsonNode v) {
        if (v == null || v.isNull()) return List.of();
        List<String> out = new ArrayList<>();
        if (v.isArray()) {
            for (JsonNode el : v) {
                if (!el.isNull()) {
                    String s = el.asText().trim();
                    if (!s.isEmpty()) out.add(s);
                }
            }
            return List.copyOf(out);
        }
        String raw = v.asText("").trim();
        if (!raw.isEmpty()) out.add(raw);
        return List.copyOf(out);
    }

    private static List<String> parseGeocodes(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return List.copyOf(out);
    }

    private static String validJsonOrNull(String raw, ObjectMapper mapper) {
        if (raw == null || raw.isBlank()) return null;
        try {
            mapper.readTree(raw);
            return raw;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
