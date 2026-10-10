package com.projeto.mapi.service.terrain.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.TerrainProperties;
import com.projeto.mapi.dto.TerrainLoadResultDTO;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.repository.terrain.TerrainRepository;
import com.projeto.mapi.repository.terrain.TerrainRepository.CurveRow;
import com.projeto.mapi.repository.terrain.TerrainRepository.NewPoint;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.terrain.TerrainLoadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Carga idempotente do terreno do PE3D (ADR 0008): um terrain_run por conteúdo distinto dos arquivos. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TerrainLoadServiceImpl implements TerrainLoadService {

    static final String POINTS_FILE = "ponto_terreno.csv";
    static final String META_FILE = "ponto_terreno.meta.json";
    static final String CURVE_FILE = "curva_cota_volume.csv";
    private static final Pattern YEAR = Pattern.compile("\\b((?:19|20)\\d{2})\\b");

    private final TerrainRepository repository;
    private final TerrainProperties props;
    private final ObjectMapper mapper;

    @Override
    public TerrainLoadResultDTO loadConfigured() {
        return load(Path.of(props.getDir()));
    }

    @Override
    @Transactional
    public TerrainLoadResultDTO load(Path dir) {
        byte[] pointsBytes = read(dir.resolve(POINTS_FILE));
        byte[] metaBytes = read(dir.resolve(META_FILE));
        byte[] curveBytes = read(dir.resolve(CURVE_FILE));
        String inputSha = Hashing.sha256(Hashing.sha256(pointsBytes) + Hashing.sha256(metaBytes)
                + Hashing.sha256(curveBytes));

        repository.lockLoad();
        var existing = repository.findRunByInputHash(inputSha);
        if (existing.isPresent()) {
            long id = existing.get();
            boolean current = repository.currentRun().map(r -> r.id() == id).orElse(false);
            log.info("Terreno já carregado (terrain_run {}), nada a fazer", id);
            return new TerrainLoadResultDTO(id, false, current, 0, 0, 0, List.of(), inputSha);
        }

        JsonNode meta = parseMeta(metaBytes);
        List<CSVRecord> pointRecords = parseCsv(pointsBytes, POINTS_FILE, "slug");
        Map<String, List<CSVRecord>> curveBySlug = new LinkedHashMap<>();
        for (CSVRecord r : parseCsv(curveBytes, CURVE_FILE, "slug", "cota_m", "altura_acima_fundo_m",
                "volume_acumulado_m3")) {
            curveBySlug.computeIfAbsent(r.get("slug"), k -> new ArrayList<>()).add(r);
        }

        String elevCol = "cota_rua_m_raio" + props.getElevationRadiusM() + "m";
        String meanCol = "declividade_media_pct_" + props.getSlopeWindowM() + "m";
        String maxCol = "declividade_max_pct_" + props.getSlopeWindowM() + "m";
        String pixCol = "n_pixels_raio" + props.getElevationRadiusM() + "m";
        requireColumns(pointRecords, POINTS_FILE, elevCol, meanCol, maxCol, pixCol);

        long runId = repository.insertCurrentRun(buildRun(meta, inputSha));
        Map<String, Long> pointIds = repository.floodPointIdsBySlug();
        int ok = 0;
        int noData = 0;
        int curveRows = 0;
        List<String> notFound = new ArrayList<>();
        for (CSVRecord r : pointRecords) {
            String slug = r.get("slug");
            Long floodPointId = pointIds.get(slug);
            if (floodPointId == null) {
                notFound.add(slug);
                continue;
            }
            Double elevation = num(r, elevCol);
            boolean hasData = elevation != null;
            long ptId = repository.insertPoint(hasData
                    ? new NewPoint(floodPointId, runId, "OK", text(r, "folha_origem"), elevation,
                            integer(r, pixCol), num(r, meanCol), num(r, maxCol), num(r, "profundidade_depressao_m"),
                            num(r, "area_contribuinte_m2"), num(r, "fundo_bacia_cota_m"),
                            num(r, "fundo_bacia_dist_ponto_m"), diagnostics(r))
                    : new NewPoint(floodPointId, runId, "NO_DATA", null, null, null, null, null, null, null, null,
                            null, null));
            if (!hasData) {
                noData++;
                continue;
            }
            ok++;
            List<CurveRow> curve = curveBySlug.getOrDefault(slug, List.of()).stream().map(TerrainLoadServiceImpl::curveRow)
                    .toList();
            repository.insertCurve(ptId, curve);
            curveRows += curve.size();
        }
        log.info("Terreno carregado: terrain_run {} ({} OK, {} NO_DATA, {} níveis de curva, {} slugs sem ponto)",
                runId, ok, noData, curveRows, notFound.size());
        return new TerrainLoadResultDTO(runId, true, true, ok, noData, curveRows, notFound, inputSha);
    }

    private TerrainRepository.NewRun buildRun(JsonNode meta, String inputSha) {
        String vrt = requireText(meta, "sha256_mosaico_vrt");
        String script = requireText(meta, "sha256_terreno_py");
        JsonNode software = meta.path("software");
        String image = software.path("imagem").asText("");
        String digest = image.contains("@") ? image.substring(image.indexOf('@') + 1) : image;
        if (digest.isBlank()) throw new InvalidRequestException(META_FILE + ": software.imagem ausente");
        Matcher y = YEAR.matcher(requireText(meta, "voo"));
        if (!y.find()) throw new InvalidRequestException(META_FILE + ": ano do voo não encontrado em 'voo'");
        List<String> sheets = new ArrayList<>();
        meta.path("folhas").forEach(n -> sheets.add(n.asText()));
        if (sheets.isEmpty()) throw new InvalidRequestException(META_FILE + ": 'folhas' vazio");

        ObjectNode parameters = mapper.createObjectNode();
        JsonNode original = meta.path("parametros");
        if (original.isObject()) parameters.setAll((ObjectNode) original);
        ObjectNode selected = parameters.putObject("selected");
        selected.put("elevationRadiusM", props.getElevationRadiusM());
        selected.put("slopeWindowM", props.getSlopeWindowM());
        parameters.put("flightDescription", meta.path("voo").asText());
        parameters.put("crs", meta.path("crs").asText());
        parameters.put("inputSha256", inputSha);
        try {
            return new TerrainRepository.NewRun(vrt, script, digest, mapper.writeValueAsString(software),
                    sheets.toArray(String[]::new), mapper.writeValueAsString(parameters),
                    Short.parseShort(y.group(1)), props.getVerticalReference(), props.getVerticalReferenceAdr());
        } catch (IOException e) {
            throw new InvalidRequestException("Falha ao serializar a proveniência: " + e.getMessage());
        }
    }

    private String diagnostics(CSVRecord r) {
        ObjectNode d = mapper.createObjectNode();
        ObjectNode snap = d.putObject("contributingAreaBySnapM2");
        for (String s : new String[] {"0", "5", "10", "20"}) {
            Double v = num(r, "area_contribuinte_m2_snap" + s + "m");
            if (v == null) snap.putNull(s + "m"); else snap.put(s + "m", v);
        }
        putBool(d, "basinTouchesWindowEdge", r, "bacia_toca_borda_janela");
        putBool(d, "basinAdjacentToNoData", r, "bacia_adjacente_a_nodata");
        Double snapDist = num(r, "snap_dist_m");
        if (snapDist != null) d.put("snapDistanceM", snapDist);
        try {
            return mapper.writeValueAsString(d);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void putBool(ObjectNode d, String name, CSVRecord r, String col) {
        String v = text(r, col);
        if (v != null) d.put(name, Boolean.parseBoolean(v));
    }

    private static CurveRow curveRow(CSVRecord r) {
        double above = Double.parseDouble(r.get("altura_acima_fundo_m"));
        return new CurveRow((int) Math.round(above * 100), Double.parseDouble(r.get("cota_m")),
                Double.parseDouble(r.get("volume_acumulado_m3")), num(r, "m3_por_cm"), num(r, "area_superficie_m2"));
    }

    private JsonNode parseMeta(byte[] bytes) {
        try {
            return mapper.readTree(bytes);
        } catch (IOException e) {
            throw new InvalidRequestException(META_FILE + " inválido: " + e.getMessage());
        }
    }

    private static List<CSVRecord> parseCsv(byte[] bytes, String name, String... required) {
        try (Reader reader = new java.io.InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true).build().parse(reader)) {
            for (String c : required) {
                if (!parser.getHeaderMap().containsKey(c)) {
                    throw new InvalidRequestException(name + ": coluna ausente: " + c);
                }
            }
            return parser.getRecords();
        } catch (IOException e) {
            throw new InvalidRequestException(name + " ilegível: " + e.getMessage());
        }
    }

    private static void requireColumns(List<CSVRecord> records, String name, String... cols) {
        if (records.isEmpty()) throw new InvalidRequestException(name + ": sem linhas");
        for (String c : cols) {
            if (!records.get(0).isMapped(c)) throw new InvalidRequestException(name + ": coluna ausente: " + c);
        }
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new InvalidRequestException("Arquivo de terreno não encontrado ou ilegível: " + file);
        }
    }

    private static String requireText(JsonNode n, String field) {
        String v = n.path(field).asText("");
        if (v.isBlank()) throw new InvalidRequestException(META_FILE + ": campo ausente: " + field);
        return v;
    }

    private static String text(CSVRecord r, String col) {
        if (!r.isMapped(col)) return null;
        String v = r.get(col);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static Double num(CSVRecord r, String col) {
        String v = text(r, col);
        return v == null ? null : Double.valueOf(v);
    }

    private static Short integer(CSVRecord r, String col) {
        Double v = num(r, col);
        return v == null ? null : (short) Math.round(v);
    }
}
