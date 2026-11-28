package com.projeto.mapi.service.limpeza;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Leitura e casamento dos chamados da Central 156 (puro, sem rede). Mesmas regras de
 * scripts/central-156/entupimento_156.py: normalização de logradouro, raio por coordenada e id = SHA-256 da linha.
 */
public final class Central156 {

    private Central156() {}

    public record Chamado(String rowSha256, int sourceYear, int groupCode, int serviceCode, String serviceDesc,
                          String logradouro, String bairro, LocalDate demandDate, LocalDate lastStatusDate, String status,
                          Double lat, Double lon) {
        public boolean atendida() { return "ATENDIDA".equals(status); }
    }

    public record PontoRef(long id, String slug, double lat, double lon, String logradouro, String bairro) {}

    public record Casamento(PontoRef ponto, String regra, Double distanciaM) {}

    private static final Set<String> PREFIXOS = Set.of("AV", "AVENIDA", "R", "RUA", "TR", "TV", "TRAVESSA", "EST", "ESTRADA",
            "PC", "PRACA", "1TR", "2TR", "3TR", "4TR", "5TR", "1TV", "2TV", "PR", "1PR", "2PR", "VL", "VILA", "LG", "LARGO",
            "CAIS", "BC", "BECO", "1BC", "2BC", "1SB", "SB");

    public static String norm(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        t = t.replaceAll("[^A-Z0-9 ]", " ").trim().replaceAll("\\s+", " ");
        return t;
    }

    public static String normLogradouro(String s) {
        List<String> p = new ArrayList<>(List.of(norm(s).split(" ")));
        while (!p.isEmpty() && PREFIXOS.contains(p.get(0))) p.remove(0);
        return String.join(" ", p);
    }

    public static double distanciaM(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371008.8, f1 = Math.toRadians(lat1), f2 = Math.toRadians(lat2);
        double a = Math.pow(Math.sin((f2 - f1) / 2), 2)
                + Math.cos(f1) * Math.cos(f2) * Math.pow(Math.sin(Math.toRadians(lon2 - lon1) / 2), 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    /** Lê o CSV anual (separador ";", UTF-8 com ou sem BOM). Linhas com colunas a mais ou a menos são descartadas. */
    public static List<Chamado> parse(String csv, int sourceYear) throws IOException {
        if (csv.startsWith("﻿")) csv = csv.substring(1);
        List<Chamado> out = new ArrayList<>();
        CSVFormat fmt = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(false).build();
        try (CSVParser p = CSVParser.parse(new StringReader(csv), fmt)) {
            List<String> cab = p.getHeaderNames().stream().map(h -> h.trim().toUpperCase(Locale.ROOT)).toList();
            for (CSVRecord r : p) {
                if (r.size() != cab.size()) continue;
                List<String> v = new ArrayList<>();
                for (String x : r) v.add(x.trim());
                String g = get(cab, v, "GRUPOSERVICO_CODIGO"), s = get(cab, v, "SERVICO_CODIGO");
                LocalDate d0 = data(get(cab, v, "DATA_DEMANDA"));
                if (g == null || s == null || d0 == null || !g.matches("\\d+") || !s.matches("\\d+")) continue;
                Double lat = num(get(cab, v, "LATITUDE")), lon = num(get(cab, v, "LONGITUDE"));
                if (lat == null || lon == null || lat < -8.2 || lat > -7.9 || lon < -35.1 || lon > -34.8) { lat = null; lon = null; }
                out.add(new Chamado(sha256(sourceYear + "|" + String.join("|", v)), sourceYear, Integer.parseInt(g),
                        Integer.parseInt(s), get(cab, v, "SERVICO_DESCRICAO"), normLogradouro(get(cab, v, "LOGRADOURO")),
                        norm(get(cab, v, "BAIRRO")), d0, data(get(cab, v, "DATA_ULT_SITUACAO")),
                        norm(get(cab, v, "SITUACAO")), lat, lon));
            }
        }
        return out;
    }

    /**
     * Pré-registro, seção 2: coordenada a até {@code raioM} do ponto, ou logradouro igual e bairro igual ao do ponto.
     * Um chamado pode casar com mais de um ponto (devolve todos).
     */
    public static List<Casamento> casar(Chamado c, List<PontoRef> pontos, double raioM) {
        return casar(c, pontos, raioM, java.util.Set.of());
    }

    /**
     * Igual, ignorando no casamento por coordenada os chamados numa coordenada-padrão (emenda 1, E1): a coordenada não
     * diz onde a boca de lobo está. Esses chamados ainda casam por logradouro e bairro.
     */
    public static List<Casamento> casar(Chamado c, List<PontoRef> pontos, double raioM, java.util.Set<String> coordenadasPadrao) {
        List<Casamento> out = new ArrayList<>();
        boolean padrao = c.lat() != null && coordenadasPadrao.contains(chaveCoordenada(c.lat(), c.lon()));
        for (PontoRef p : pontos) {
            if (c.lat() != null && !padrao) {
                double d = distanciaM(p.lat(), p.lon(), c.lat(), c.lon());
                if (d <= raioM) { out.add(new Casamento(p, "COORDENADA_150M", d)); continue; }
            }
            if (p.logradouro() != null && p.logradouro().equals(c.logradouro()) && p.bairro() != null && p.bairro().equals(c.bairro())) {
                out.add(new Casamento(p, "LOGRADOURO_BAIRRO", null));
            }
        }
        return out;
    }

    /** Coordenada arredondada a 5 casas (~1 m), como no script da emenda 1. */
    public static String chaveCoordenada(double lat, double lon) {
        return String.format(java.util.Locale.ROOT, "%.5f,%.5f", lat, lon);
    }

    /** P(entupida | dias) = 1 - exp(-lambda · dias); vazio sem dado. */
    public static Optional<Double> pEntupida(Double dias, Double lambda) {
        if (dias == null || lambda == null || dias < 0) return Optional.empty();
        return Optional.of(1 - Math.exp(-lambda * dias));
    }

    private static String get(List<String> cab, List<String> v, String col) {
        int i = cab.indexOf(col);
        return i < 0 || v.get(i).isEmpty() ? null : v.get(i);
    }

    private static LocalDate data(String s) {
        if (s == null || s.length() < 10) return null;
        try { return LocalDate.parse(s.substring(0, 10)); } catch (Exception e) { return null; }
    }

    private static Double num(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
