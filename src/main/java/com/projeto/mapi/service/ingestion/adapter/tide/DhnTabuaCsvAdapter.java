package com.projeto.mapi.service.ingestion.adapter.tide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.exception.SourceUnavailableException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tábua anual da DHN/CHM (Porto do Recife, carta 902) para os anos que a API TabuaMare não serve (ela só entrega o
 * ano corrente). Lê {@code tide/dhn-pe02-<ano>.csv}, extraído do PDF oficial por
 * {@code scripts/tabuas-dhn/extrair_tabua_dhn.py} (com SHA-256 do PDF na primeira linha do CSV).
 * Hora local da tábua (UTC-3), altura em m acima do NR da DHN. Resolução da altura: 0,1 m em 2021-2024 e 0,01 m em
 * 2025-2026 (formato do PDF).
 */
@Component
public class DhnTabuaCsvAdapter extends AbstractTideExtremesAdapter {

    public static final String SOURCE = "DHN_TABUA_PE02";
    static final String RESOURCE = "tide/dhn-pe02-%d.csv";

    private final ObjectMapper mapper;

    public DhnTabuaCsvAdapter(ObjectMapper mapper, Clock clock) {
        super(mapper, clock);
        this.mapper = mapper;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public String parserVersion() {
        return "dhn-tabua-csv/1";
    }

    @Override
    protected Loaded load(LocalDate firstDay, LocalDate lastDay, LocalDate coreFirst, LocalDate coreLast) {
        List<TideExtreme> out = new ArrayList<>();
        ArrayNode files = mapper.createArrayNode();
        for (int year = firstDay.getYear(); year <= lastDay.getYear(); year++) {
            ClassPathResource res = new ClassPathResource(String.format(RESOURCE, year));
            if (!res.exists()) continue;
            ObjectNode f = files.addObject();
            f.put("file", res.getPath());
            try (BufferedReader r = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("#")) {
                        int i = line.indexOf("pdf sha256=");
                        if (i >= 0) f.put("pdf_sha256", line.substring(i + "pdf sha256=".length()).trim());
                        continue;
                    }
                    if (line.isBlank()) continue;
                    String[] c = line.split(",");
                    if (c.length != 3) throw new SourceUnavailableException("Tábua DHN: linha inválida em " + res.getPath() + ": " + line);
                    LocalDate d = LocalDate.parse(c[0]);
                    if (d.isBefore(firstDay) || d.isAfter(lastDay)) continue;
                    out.add(new TideExtreme(d, LocalTime.parse(c[1]), Double.parseDouble(c[2])));
                }
            } catch (IOException | RuntimeException e) {
                throw new SourceUnavailableException("Tábua DHN ilegível: " + res.getPath(), e);
            }
        }
        if (out.isEmpty()) {
            throw new SourceUnavailableException("Sem tábua DHN no recurso para " + firstDay + " a " + lastDay
                    + " (anos disponíveis: 2021 a 2026)");
        }
        ObjectNode req = mapper.createObjectNode();
        req.put("origin", "classpath tide/dhn-pe02-<ano>.csv (PDF oficial DHN/CHM)");
        req.put("timezone", TideConstants.DECLARED_TZ);
        req.set("files", files);
        return new Loaded(out, req);
    }
}
