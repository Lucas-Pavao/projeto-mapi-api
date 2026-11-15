package com.projeto.mapi.service.analise;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checagem espacial mensal de pluviômetros (ADR 0022). Pura: sem banco nem Spring.
 *
 * <p>Para cada estação e mês, compara a estação com as vizinhas nas MESMAS horas (horas em que as duas têm valor), para
 * que lacuna de uma não pareça falta de chuva. Quatro defeitos reprovam o mês:
 * <ul>
 *   <li>{@code PARTIAL_COVERAGE}: nas horas COM valor, os blocos aceitos cobrem menos que
 *       {@link Rules#minBlockCoverage()} do tempo. Pega a estação que transmite só parte dos blocos: na regra da V22
 *       o bloco que falta numa hora com leitura conta como zero, então o volume sai baixo sem a hora virar lacuna.
 *       Dia inteiro sem transmissão NÃO conta aqui: já é lacuna e não distorce valor (chuva-ponto-v2; na v1 a
 *       cobertura era sobre o mês inteiro e reprovava estação boa com dias parados);</li>
 *   <li>{@code STUCK_ZERO}: total da estação abaixo de {@link Rules#stuckMaxMm()} nas horas em comum, com a mediana das
 *       vizinhas acima de {@link Rules#minNeighborMm()} (pluviômetro travado);</li>
 *   <li>{@code UNDERCATCH}: mediana da razão estação/vizinha abaixo de {@link Rules#minRatio()} (entupimento);</li>
 *   <li>{@code OVERCATCH}: mediana acima de {@link Rules#maxRatio()}. Com a seleção do maior valor, uma estação que
 *       exagera domina a série do ponto; por isso o excesso também reprova.</li>
 * </ul>
 * Sem ao menos {@link Rules#minNeighbors()} vizinhas com chuva suficiente nas horas em comum, a estação fica
 * {@code UNCHECKED} (usável, com o motivo). Sem nenhuma hora com valor no mês, {@code OFFLINE}.
 */
public final class StationMonthChecker {

    private StationMonthChecker() {}

    /** Limiares; todos {@code [sem fonte]} como critério, declarados no ADR 0022 e no resultado. */
    public record Rules(double neighborRadiusM, double minBlockCoverage, double minNeighborMm, int minNeighbors,
                        double stuckMaxMm, double minRatio, double maxRatio) {

        public static Rules defaults() {
            return new Rules(10000.0, 0.80, 20.0, 2, 1.0, 0.5, 2.0);
        }

        public Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("neighborRadiusM", neighborRadiusM);
            m.put("minBlockCoverage", minBlockCoverage);
            m.put("minNeighborMm", minNeighborMm);
            m.put("minNeighbors", minNeighbors);
            m.put("stuckMaxMm", stuckMaxMm);
            m.put("minRatio", minRatio);
            m.put("maxRatio", maxRatio);
            return m;
        }
    }

    /**
     * Série de uma estação no mês. {@code mm[i]} = chuva da i-ésima hora (NaN = sem valor); {@code coveredMin[i]} =
     * minutos cobertos pelos blocos da hora (NaN = desconhecido).
     */
    public record StationMonth(String stationId, double lat, double lon, double[] mm, double[] coveredMin) {}

    public record Result(String stationId, String status, List<String> reasons, int hoursInMonth, int hoursWithValue,
                         double hourCoverage, double blockCoverage, double blockCoverageInHours, Double totalMm,
                         int nNeighbors,
                         Double ratioMedian, Double commonMmMedian, List<String> neighborsUsed) {

        public boolean usable() {
            return "PASS".equals(status) || "UNCHECKED".equals(status);
        }
    }

    public static List<Result> check(List<StationMonth> stations, Rules r) {
        List<Result> out = new ArrayList<>();
        for (StationMonth s : stations) out.add(checkOne(s, stations, r));
        return out;
    }

    static Result checkOne(StationMonth s, List<StationMonth> all, Rules r) {
        int n = s.mm().length;
        int withValue = 0;
        double total = 0, covered = 0;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(s.mm()[i])) continue;
            withValue++;
            total += s.mm()[i];
            if (!Double.isNaN(s.coveredMin()[i])) covered += s.coveredMin()[i];
        }
        double hourCov = n == 0 ? 0 : (double) withValue / n;
        double blockCov = n == 0 ? 0 : Math.min(1.0, covered / (n * 60.0));
        if (withValue == 0) {
            return new Result(s.stationId(), "OFFLINE", List.of("NO_DATA_IN_MONTH"), n, 0, 0, 0, 0, null, 0, null, null,
                    List.of());
        }
        double blockCovInHours = Math.min(1.0, covered / (withValue * 60.0));

        List<Double> ratios = new ArrayList<>();
        List<Double> commonN = new ArrayList<>();
        List<Double> commonS = new ArrayList<>();
        List<String> used = new ArrayList<>();
        for (StationMonth o : all) {
            if (o == s || o.stationId().equals(s.stationId())) continue;
            if (distanceM(s.lat(), s.lon(), o.lat(), o.lon()) > r.neighborRadiusM()) continue;
            double ss = 0, nn = 0;
            for (int i = 0; i < n; i++) {
                double a = s.mm()[i], b = o.mm()[i];
                if (Double.isNaN(a) || Double.isNaN(b)) continue;
                ss += a;
                nn += b;
            }
            if (nn < r.minNeighborMm()) continue; // vizinha sem chuva suficiente nas horas em comum: não informa
            ratios.add(ss / nn);
            commonN.add(nn);
            commonS.add(ss);
            used.add(o.stationId());
        }

        List<String> reasons = new ArrayList<>();
        if (blockCovInHours < r.minBlockCoverage()) reasons.add("PARTIAL_COVERAGE");
        Double ratioMed = ratios.isEmpty() ? null : median(ratios);
        Double nMed = commonN.isEmpty() ? null : median(commonN);
        if (ratios.size() >= r.minNeighbors()) {
            if (median(commonS) < r.stuckMaxMm()) reasons.add("STUCK_ZERO");
            else if (ratioMed < r.minRatio()) reasons.add("UNDERCATCH");
            if (ratioMed > r.maxRatio()) reasons.add("OVERCATCH");
        }
        String status;
        if (!reasons.isEmpty()) {
            status = "FAIL";
        } else if (ratios.size() < r.minNeighbors()) {
            status = "UNCHECKED";
            reasons.add("FEW_WET_NEIGHBORS");
        } else {
            status = "PASS";
        }
        return new Result(s.stationId(), status, reasons, n, withValue, hourCov, blockCov, blockCovInHours, total,
                ratios.size(),
                ratioMed, nMed, used);
    }

    static double median(List<Double> v) {
        List<Double> s = new ArrayList<>(v);
        s.sort(Double::compare);
        int k = s.size();
        return k % 2 == 1 ? s.get(k / 2) : (s.get(k / 2 - 1) + s.get(k / 2)) / 2.0;
    }

    public static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * 6371000.0 * Math.asin(Math.sqrt(a));
    }
}
