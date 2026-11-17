package com.projeto.mapi.service.analise;

import java.util.List;

/**
 * Chuva horária do ponto pela regra do ADR 0022 (pura): entre as estações candidatas (dentro do raio) aprovadas no mês,
 * o MAIOR valor da hora; empate fica com a mais próxima. Sem estação válida com valor, a hora é lacuna (nunca zero).
 */
public final class PointRainComposer {

    private PointRainComposer() {}

    /** Uma candidata na hora: valor (NaN = sem valor), se está válida no mês, distância e composição da hora. */
    public record Candidate(String stationId, double distanceM, boolean valid, double mm, int nBlocks,
                            double coveredMin) {}

    public record Hour(Double value, String stationId, Double distanceM, int nCandidates, int nValid,
                       int nValidWithValue, Integer nBlocks, Double coveredMin) {}

    public enum RainTier { MEASURED_PRIMARY, MEASURED_EXTENDED, REANALYSIS_FALLBACK }

    public record RainProvenance(RainTier tier, double confidence, String flag) {}

    public static Hour compose(List<Candidate> cs) {
        Candidate best = null;
        int valid = 0, withValue = 0;
        for (Candidate c : cs) {
            if (!c.valid()) continue;
            valid++;
            if (Double.isNaN(c.mm())) continue;
            withValue++;
            if (best == null || c.mm() > best.mm() || (c.mm() == best.mm() && c.distanceM() < best.distanceM())) best = c;
        }
        if (best == null) return new Hour(null, null, null, cs.size(), valid, 0, null, null);
        return new Hour(best.mm(), best.stationId(), best.distanceM(), cs.size(), valid, withValue,
                best.nBlocks(), Double.isNaN(best.coveredMin()) ? null : best.coveredMin());
    }

    /** Proveniência da hora composta sem alterar o valor escolhido pelo ADR 0022. */
    public static RainProvenance classifyProvenance(Double value, Double distanceM, double primaryRadiusM,
                                                    double maxRadiusM, boolean reanalysisFallback,
                                                    double distantConfidence, double reanalysisConfidence) {
        if (reanalysisFallback) {
            return new RainProvenance(RainTier.REANALYSIS_FALLBACK, reanalysisConfidence, "SYNTHETIC_RAIN_FALLBACK");
        }
        if (value == null || distanceM == null) return null;
        if (distanceM <= primaryRadiusM) {
            return new RainProvenance(RainTier.MEASURED_PRIMARY, 1.0, null);
        }
        if (distanceM <= maxRadiusM) {
            return new RainProvenance(RainTier.MEASURED_EXTENDED, distantConfidence, "DISTANT_STATION");
        }
        return null;
    }
}
