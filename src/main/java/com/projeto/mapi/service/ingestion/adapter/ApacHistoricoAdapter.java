package com.projeto.mapi.service.ingestion.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.exception.SourceUnavailableException;
import com.projeto.mapi.service.ingestion.DraftValue;
import com.projeto.mapi.service.ingestion.FetchedBatch;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.StationInfo;
import com.projeto.mapi.service.ingestion.validation.TimestampNormalizer;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Histórico pluviométrico da APAC (ADR 0020; {@code docs/REVISAO-INGESTAO-2026-11.md}, seção 1): total do dia civil
 * local por posto, da planilha exportada pelo formulário. Só diário: entra como {@code precipitation_daily_total}
 * ({@code P1D}), fora da grade horária.
 *
 * <p>Um registro de origem por (posto x dia) com valor; {@code -} na célula é ausência e não gera registro (nunca
 * zero). O XLSX inteiro tem o SHA-256 na proveniência do lote. Cabeçalho diferente do esperado ou planilha sem postos
 * depois da consulta = falha visível ({@link SourceUnavailableException}), nunca lote vazio silencioso.
 */
@Component
@RequiredArgsConstructor
public class ApacHistoricoAdapter implements SourceAdapter {

    public static final String SOURCE = "APAC_HIST_DIARIO";
    static final String VARIABLE = "precipitation_daily_total";
    static final List<String> HEADER_PREFIX = List.of("Código GMMC", "Identificador", "Município", "Estação",
            "Latitude", "Longitude", "Microrregião", "Mesorregião", "Bacia", "Ano/Mês", "01");
    private static final ZoneId RECIFE = ZoneId.of("America/Recife");

    private final ApacHistoricoClient client;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public String declaredTz() {
        return "America/Recife";
    }

    @Override
    public String parserVersion() {
        return "apac-hist-xlsx/1";
    }

    /** Um mês civil por requisição (a planilha tem uma coluna por dia do mês). */
    @Override
    public void fetch(Instant from, Instant to, Consumer<FetchedBatch> sink) {
        LocalDate first = from.atZone(RECIFE).toLocalDate();
        LocalDate last = to.minusNanos(1).atZone(RECIFE).toLocalDate();
        LocalDate yesterday = clock.instant().atZone(RECIFE).toLocalDate().minusDays(1);
        if (last.isAfter(yesterday)) last = yesterday;
        for (YearMonth m = YearMonth.from(first); !m.atDay(1).isAfter(last); m = m.plusMonths(1)) {
            LocalDate s = m.atDay(1).isBefore(first) ? first : m.atDay(1);
            LocalDate e = m.atEndOfMonth().isAfter(last) ? last : m.atEndOfMonth();
            sink.accept(parse(client.daily(s, e), s, e));
        }
    }

    FetchedBatch parse(ApacHistoricoClient.Response resp, LocalDate first, LocalDate last) {
        List<List<String>> rows = sheet(resp.body());
        if (rows.isEmpty() || !rows.get(0).subList(0, Math.min(rows.get(0).size(), HEADER_PREFIX.size()))
                .equals(HEADER_PREFIX)) {
            throw new SourceUnavailableException("APAC histórico: cabeçalho da planilha mudou ("
                    + (rows.isEmpty() ? "vazia" : rows.get(0)) + "); extrator não confia no layout");
        }
        if (rows.size() < 2) {
            throw new SourceUnavailableException("APAC histórico: planilha sem postos para " + first + " a " + last
                    + " depois da consulta (" + resp.filters() + ")");
        }
        Instant collectedAt = clock.instant();
        List<RawRecord> records = new ArrayList<>();
        List<StationInfo> stations = new ArrayList<>();
        for (List<String> r : rows.subList(1, rows.size())) {
            if (r.size() < 11 || r.get(0).isBlank()) continue;
            String code = r.get(0).trim();
            String[] ym = r.get(9).trim().split("/");
            YearMonth month = YearMonth.of(Integer.parseInt(ym[0]), Integer.parseInt(ym[1]));
            ObjectNode attrs = mapper.createObjectNode();
            attrs.put("identificador", r.get(1).trim());
            attrs.put("tipo", tipo(r.get(3)));
            attrs.put("microrregiao", r.get(6).trim());
            attrs.put("mesorregiao", r.get(7).trim());
            attrs.put("bacia", r.get(8).trim());
            stations.add(new StationInfo(SOURCE, code, r.get(3).trim(), coord(r.get(4)), coord(r.get(5)),
                    r.get(2).trim(), attrs));
            for (int d = 1; d <= month.lengthOfMonth() && 9 + d < r.size(); d++) {
                String cell = r.get(9 + d).trim();
                if (cell.isEmpty() || "-".equals(cell)) continue; // ausência, nunca zero
                LocalDate day = month.atDay(d);
                if (day.isBefore(first) || day.isAfter(last)) continue;
                ObjectNode payload = mapper.createObjectNode();
                payload.put("codigo", code);
                payload.put("estacao", r.get(3).trim());
                payload.put("dia", day.toString());
                payload.put("valor", cell);
                payload.put("unidade", "mm");
                records.add(new RawRecord(SOURCE, code, collectedAt, day.toString(),
                        TimestampNormalizer.normalize(day.toString()), declaredTz(), payload));
            }
        }
        ObjectNode req = mapper.createObjectNode();
        req.put("url", resp.url());
        req.put("filters", resp.filters());
        req.put("sha256", Hashing.sha256(resp.body()));
        req.put("format", "xlsx");
        return new FetchedBatch(records, stations, req);
    }

    @Override
    public List<DraftValue> extract(RawRecord record) {
        Double v = number(record.payload().path("valor").asText(""));
        if (v == null) return List.of();
        return List.of(new DraftValue(VARIABLE, "P1D", v, "mm", null));
    }

    /** "137,40" → 137.4; texto não numérico → nulo (vira registro sem valor, não zero). */
    static Double number(String text) {
        String t = text.trim();
        if (t.indexOf(',') >= 0) t = t.replace(".", "").replace(',', '.');
        if (t.isEmpty() || "-".equals(t)) return null;
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double coord(String text) {
        String t = text.trim().replace("'", "");
        try {
            return t.isEmpty() ? null : Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "[Convencional]", "[APAC] Sede", "[CEMADEN] Pina" → CONVENCIONAL, APAC, CEMADEN. */
    static String tipo(String estacao) {
        String s = estacao.toUpperCase();
        if (s.contains("[CONVENCIONAL]")) return "CONVENCIONAL";
        if (s.startsWith("[CEMADEN")) return "CEMADEN";
        if (s.startsWith("[APAC]")) return "APAC";
        return "OUTRO";
    }

    private static List<List<String>> sheet(byte[] xlsx) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            Sheet sh = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            List<List<String>> out = new ArrayList<>();
            for (Row row : sh) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    cells.add(cell == null ? "" : fmt.formatCellValue(cell));
                }
                out.add(cells);
            }
            return out;
        } catch (IOException | RuntimeException e) {
            throw new SourceUnavailableException("APAC histórico: planilha ilegível (não é XLSX válido)", e);
        }
    }
}
