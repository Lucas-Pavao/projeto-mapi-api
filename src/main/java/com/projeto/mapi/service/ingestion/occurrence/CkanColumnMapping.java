package com.projeto.mapi.service.ingestion.occurrence;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mapeamento de colunas por recurso do CKAN (F2-BACK-18). Os recursos do portal nem sempre têm o conteúdo sob o
 * nome de coluna esperado: no recurso de 2022 o cabeçalho é o mesmo dos outros anos, mas os valores estão
 * deslocados (Regional = ano, Ano = nome do mês, "Mês" = data ISO, Data = tipo da ocorrência, Ocorrencia = tipo
 * do serviço, Solicitacao = logradouro, Endereco = lixo). Por isso o mapeamento é escolhido pelo PERFIL DOS
 * VALORES do recurso (cabeçalho + conteúdo), nunca por ano fixo.
 *
 * <p>O bruto ({@code raw_observation.payload}) não é alterado: o mapeamento só produz a visão canônica
 * ({@code Data}, {@code Ocorrencia}, {@code Endereco}, {@code Bairro}) usada pela classificação e pelo geocoding.
 */
public record CkanColumnMapping(String name, String dateColumn, String typeColumn, String addressColumn,
                                String neighborhoodColumn) {

    /** Esquema documentado do dataset: Data, Ocorrencia, Endereco, Bairro nos seus próprios campos. */
    public static final CkanColumnMapping STANDARD =
            new CkanColumnMapping("standard", "Data", "Ocorrencia", "Endereco", "Bairro");

    /** Esquema deslocado (recurso de 2022): data em "Mês", tipo em "Data", logradouro (sem número) em "Solicitacao". */
    public static final CkanColumnMapping SHIFTED_DATE_IN_MONTH =
            new CkanColumnMapping("shifted-date-in-month", "Mês", "Data", "Solicitacao", "Bairro");

    private static final Pattern DATE_LIKE =
            Pattern.compile("^\\s*(\\d{4}-\\d{2}-\\d{2}([T ].*)?|\\d{2}/\\d{2}/\\d{4}.*)$");
    private static final Pattern YEAR = Pattern.compile("^\\s*(19|20)\\d{2}\\s*$");

    /**
     * Escolhe o mapeamento pelo perfil dos registros do recurso: conta quantos têm data em {@code Data} (padrão) e
     * quantos têm ano em {@code Regional}, data na coluna do mês e não-data em {@code Data} (deslocado). Vence o
     * perfil com mais ocorrências; sem evidência de deslocamento, o padrão.
     */
    public static CkanColumnMapping detect(Collection<Map<String, Object>> records) {
        long standard = 0;
        long shifted = 0;
        for (Map<String, Object> r : records) {
            String data = value(r, "Data");
            if (isDateLike(data)) {
                standard++;
                continue;
            }
            if (YEAR.matcher(value(r, "Regional")).matches() && isDateLike(monthColumnValue(r))) {
                shifted++;
            }
        }
        return shifted > standard ? SHIFTED_DATE_IN_MONTH : STANDARD;
    }

    /** Visão canônica de um registro bruto (chaves Data, Ocorrencia, Endereco, Bairro) segundo este mapeamento. */
    public Map<String, Object> canonical(Map<String, Object> fields) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("Data", column(fields, dateColumn));
        out.put("Ocorrencia", column(fields, typeColumn));
        out.put("Endereco", column(fields, addressColumn));
        out.put("Bairro", column(fields, neighborhoodColumn));
        return out;
    }

    private Object column(Map<String, Object> fields, String column) {
        return "mes".equals(fold(unmojibake(column))) ? monthColumn(fields) : raw(fields, column);
    }

    private static Object raw(Map<String, Object> fields, String key) {
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return null;
    }

    private static String value(Map<String, Object> fields, String key) {
        Object v = raw(fields, key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    /** A coluna do mês aparece como "Mês" ou, com a codificação quebrada na origem, "MÃªs" (UTF-8 lido como Latin-1). */
    private static Object monthColumn(Map<String, Object> fields) {
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (e.getKey() != null && "mes".equals(fold(unmojibake(e.getKey())))) return e.getValue();
        }
        return null;
    }

    private static String monthColumnValue(Map<String, Object> fields) {
        Object v = monthColumn(fields);
        return v == null ? "" : String.valueOf(v).trim();
    }

    static String unmojibake(String s) {
        if (s.indexOf('Ã') < 0) return s;
        return new String(s.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    private static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static boolean isDateLike(String s) {
        return s != null && DATE_LIKE.matcher(s).matches();
    }
}
