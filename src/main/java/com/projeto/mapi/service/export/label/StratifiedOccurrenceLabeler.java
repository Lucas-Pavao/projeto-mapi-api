package com.projeto.mapi.service.export.label;

import com.projeto.mapi.config.ExportLabelProperties;
import com.projeto.mapi.service.analise.RainEventDetector.DetectedEvent;
import com.projeto.mapi.service.geocoding.GeocodeLevel;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Classificação estratificada de ocorrências (Opção B / ADR 0025, proposta) e casamento temporal com picos
 * de chuva do {@code RainEventDetector} (ADR 0017). Lógica pura, sem I/O.
 *
 * <p>Ocorrências com precisão {@code DAY} (sem hora) nunca são casadas pela janela [-2h, +6h] do pico: recebem
 * {@link TemporalMatch#TEMPORAL_DAY_ONLY} e seguem a regra de dia já existente no {@link OccurrenceLabeler}.
 */
public final class StratifiedOccurrenceLabeler {

    public enum Tier { HIGH, MEDIUM, LOW, DISCARD }

    public enum TemporalMatch { EVENT_WINDOW, TEMPORAL_DAY_ONLY, OUTSIDE_EVENT_WINDOW }

    public record OccurrenceCandidate(
            long id,
            Instant from,
            Instant to,
            boolean hour,
            String geocodeMethod,
            GeocodeLevel geocodeLevel,
            double distanceM,
            boolean validatedReport,
            int clusterSize
    ) {}

    public record ClassifiedOccurrence(
            long id,
            Tier tier,
            double sampleWeight,
            TemporalMatch temporalMatch,
            boolean matchedEventWindow
    ) {}

    private StratifiedOccurrenceLabeler() {}

    public static ClassifiedOccurrence classify(OccurrenceCandidate occ, List<DetectedEvent> rainEvents,
                                                ExportLabelProperties props) {
        Tier tier = classifyTier(occ, props);
        double weight = weightOf(tier, props);
        if (!occ.hour()) {
            return new ClassifiedOccurrence(occ.id(), tier, weight, TemporalMatch.TEMPORAL_DAY_ONLY, false);
        }
        boolean matched = matchesAnyPeakWindow(occ.from(), rainEvents,
                props.getWindowBeforeHours(), props.getWindowAfterHours());
        TemporalMatch tm = matched ? TemporalMatch.EVENT_WINDOW : TemporalMatch.OUTSIDE_EVENT_WINDOW;
        return new ClassifiedOccurrence(occ.id(), tier, weight, tm, matched);
    }

    public static Tier classifyTier(OccurrenceCandidate occ, ExportLabelProperties props) {
        if (occ == null || occ.distanceM() < 0) return Tier.DISCARD;
        boolean sourceCoord = "SOURCE_COORDINATE".equalsIgnoreCase(occ.geocodeMethod());
        boolean addressLevel = occ.geocodeLevel() == GeocodeLevel.ADDRESS;
        if ((occ.validatedReport() || sourceCoord || addressLevel) && occ.distanceM() <= props.getRadiusPointM()) {
            return Tier.HIGH;
        }
        if (occ.geocodeLevel() == GeocodeLevel.STREET && occ.distanceM() <= props.getRadiusStreetM()) {
            return Tier.MEDIUM;
        }
        if (occ.geocodeLevel() == GeocodeLevel.NEIGHBORHOOD
                && occ.clusterSize() >= props.getMinClusterSizeNeighborhood()
                && occ.distanceM() <= props.getRadiusClusterM()) {
            return Tier.LOW;
        }
        return Tier.DISCARD;
    }

    public static double weightOf(Tier tier, ExportLabelProperties props) {
        return switch (tier) {
            case HIGH -> props.getWeights().getHigh();
            case MEDIUM -> props.getWeights().getMedium();
            case LOW -> props.getWeights().getLow();
            case DISCARD -> 0.0;
        };
    }

    /** Verdadeiro se {@code occTime} cai em [pico - beforeH, pico + afterH] de algum evento detectado. */
    public static boolean matchesAnyPeakWindow(Instant occTime, List<DetectedEvent> rainEvents,
                                               int beforeHours, int afterHours) {
        if (occTime == null || rainEvents == null || rainEvents.isEmpty()) return false;
        for (DetectedEvent ev : rainEvents) {
            Instant peak = ev.startUtc().plus(ev.peakOffsetMin(), ChronoUnit.MINUTES);
            Instant lo = peak.minus(beforeHours, ChronoUnit.HOURS);
            Instant hi = peak.plus(afterHours, ChronoUnit.HOURS);
            if (!occTime.isBefore(lo) && !occTime.isAfter(hi)) {
                return true;
            }
        }
        return false;
    }
}
