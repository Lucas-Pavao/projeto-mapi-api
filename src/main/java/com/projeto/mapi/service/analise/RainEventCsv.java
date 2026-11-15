package com.projeto.mapi.service.analise;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CSV determinístico de uma lista de records: cabeçalho = nomes dos componentes, na ordem declarada; nulo = célula
 * vazia; número sem notação científica; lista separada por ';'. O mesmo conteúdo dá sempre o mesmo texto, que é o
 * que o {@code X-Dataset-Id} (SHA-256) identifica.
 */
public final class RainEventCsv {

    private RainEventCsv() {
    }

    public static <T extends Record> String write(Class<T> type, List<T> rows) {
        RecordComponent[] cols = type.getRecordComponents();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(cols[i].getName());
        }
        sb.append('\n');
        for (T row : rows) {
            for (int i = 0; i < cols.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(cell(value(cols[i], row)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static Object value(RecordComponent c, Object row) {
        try {
            return c.getAccessor().invoke(row);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static String cell(Object v) {
        if (v == null) return "";
        String s;
        if (v instanceof Double d) {
            s = d.isNaN() || d.isInfinite() ? "" : BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        } else if (v instanceof List<?> l) {
            s = l.stream().map(String::valueOf).collect(Collectors.joining(";"));
        } else {
            s = String.valueOf(v);
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) s = '"' + s.replace("\"", "\"\"") + '"';
        return s;
    }
}
