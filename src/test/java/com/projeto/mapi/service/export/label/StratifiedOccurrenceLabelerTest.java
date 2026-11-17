package com.projeto.mapi.service.export.label;

import com.projeto.mapi.config.ExportLabelProperties;
import com.projeto.mapi.service.analise.RainEventDetector;
import com.projeto.mapi.service.analise.RainEventDetector.DetectedEvent;
import com.projeto.mapi.service.export.label.StratifiedOccurrenceLabeler.OccurrenceCandidate;
import com.projeto.mapi.service.export.label.StratifiedOccurrenceLabeler.TemporalMatch;
import com.projeto.mapi.service.export.label.StratifiedOccurrenceLabeler.Tier;
import com.projeto.mapi.service.geocoding.GeocodeLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StratifiedOccurrenceLabelerTest {

    private final ExportLabelProperties props = new ExportLabelProperties();
    private final List<DetectedEvent> events = List.of(
            // evento de 4 h, início 12:00Z, pico às +120 min = 14:00Z -> janela [12:00Z, 20:00Z]
            new DetectedEvent(0, 3, Instant.parse("2026-05-08T12:00:00Z"), Instant.parse("2026-05-08T16:00:00Z"),
                    240, 40.0, 10.0, 20.0, 120, null, 4, 4, false, false, Set.of(), 0, 0.0)
    );

    @Test
    void estratosEspaciaisEBordasDeRaio() {
        Instant t = Instant.parse("2026-05-08T14:00:00Z");
        // OURO: ADDRESS <= 250 m, SOURCE_COORDINATE <= 250 m, ou FloodReport validado <= 250 m
        assertThat(StratifiedOccurrenceLabeler.classifyTier(
                new OccurrenceCandidate(1, t, t, true, "GEOCODING_SERVICE", GeocodeLevel.ADDRESS, 250.0, false, 1), props))
                .isEqualTo(Tier.HIGH);
        assertThat(StratifiedOccurrenceLabeler.classifyTier(
                new OccurrenceCandidate(2, t, t, true, "SOURCE_COORDINATE", GeocodeLevel.STREET, 100.0, false, 1), props))
                .isEqualTo(Tier.HIGH);
        assertThat(StratifiedOccurrenceLabeler.classifyTier(
                new OccurrenceCandidate(3, t, t, true, "GEOCODING_SERVICE", GeocodeLevel.NEIGHBORHOOD, 200.0, true, 1), props))
                .isEqualTo(Tier.HIGH);
        assertThat(StratifiedOccurrenceLabeler.classifyTier(
                new OccurrenceCandidate(4, t, t, true, "GEOCODING_SERVICE", GeocodeLevel.ADDRESS, 251.0, false, 1), props))
                .isEqualTo(Tier.DISCARD);

        // PRATA: STREET <= 400 m
        assertThat(StratifiedOccurrenceLabeler.classifyTier(
                new OccurrenceCandidate(5, t, t, true, "GEOCODING_SERVICE", GeocodeLevel.STREET, 400.0, false, 1), props))
                .isEqualTo(Tier.MEDIUM);
        assertThat(StratifiedOccurrenceLabeler.classifyTier(
                new OccurrenceCandidate(6, t, t, true, "GEOCODING_SERVICE", GeocodeLevel.STREET, 401.0, false, 1), props))
                .isEqualTo(Tier.DISCARD);

        // BRONZE: NEIGHBORHOOD com cluster >= 3 e <= 600 m; isolado ou cluster=2 é descartado
        assertThat(StratifiedOccurrenceLabeler.classifyTier(
                new OccurrenceCandidate(7, t, t, true, "GEOCODING_SERVICE", GeocodeLevel.NEIGHBORHOOD, 600.0, false, 3), props))
                .isEqualTo(Tier.LOW);
        assertThat(StratifiedOccurrenceLabeler.classifyTier(
                new OccurrenceCandidate(8, t, t, true, "GEOCODING_SERVICE", GeocodeLevel.NEIGHBORHOOD, 150.0, false, 2), props))
                .isEqualTo(Tier.DISCARD);

        // DESCARTE: CITY / NONE
        assertThat(StratifiedOccurrenceLabeler.classifyTier(
                new OccurrenceCandidate(9, t, t, true, "GEOCODING_SERVICE", GeocodeLevel.CITY, 50.0, false, 5), props))
                .isEqualTo(Tier.DISCARD);
    }

    @Test
    void janelaTemporalPicoMenos2Mais6_eDaySempreTemporalDayOnly() {
        // Pico = 14:00Z -> bordas 12:00Z e 20:00Z
        var dentroInicio = StratifiedOccurrenceLabeler.classify(
                new OccurrenceCandidate(1, Instant.parse("2026-05-08T12:00:00Z"), Instant.parse("2026-05-08T12:00:01Z"),
                        true, "GEOCODING_SERVICE", GeocodeLevel.ADDRESS, 100.0, false, 1), events, props);
        assertThat(dentroInicio.temporalMatch()).isEqualTo(TemporalMatch.EVENT_WINDOW);
        assertThat(dentroInicio.matchedEventWindow()).isTrue();
        assertThat(dentroInicio.sampleWeight()).isEqualTo(1.0);

        var dentroFim = StratifiedOccurrenceLabeler.classify(
                new OccurrenceCandidate(2, Instant.parse("2026-05-08T20:00:00Z"), Instant.parse("2026-05-08T20:00:01Z"),
                        true, "GEOCODING_SERVICE", GeocodeLevel.STREET, 300.0, false, 1), events, props);
        assertThat(dentroFim.temporalMatch()).isEqualTo(TemporalMatch.EVENT_WINDOW);
        assertThat(dentroFim.sampleWeight()).isEqualTo(0.7);

        var fora = StratifiedOccurrenceLabeler.classify(
                new OccurrenceCandidate(3, Instant.parse("2026-05-08T20:00:01Z"), Instant.parse("2026-05-08T20:00:02Z"),
                        true, "GEOCODING_SERVICE", GeocodeLevel.ADDRESS, 100.0, false, 1), events, props);
        assertThat(fora.temporalMatch()).isEqualTo(TemporalMatch.OUTSIDE_EVENT_WINDOW);
        assertThat(fora.matchedEventWindow()).isFalse();

        // Ocorrência DAY nunca casa pela janela do pico
        var day = StratifiedOccurrenceLabeler.classify(
                new OccurrenceCandidate(4, Instant.parse("2026-05-08T03:00:00Z"), Instant.parse("2026-05-09T03:00:00Z"),
                        false, "GEOCODING_SERVICE", GeocodeLevel.NEIGHBORHOOD, 500.0, false, 3), events, props);
        assertThat(day.tier()).isEqualTo(Tier.LOW);
        assertThat(day.sampleWeight()).isEqualTo(0.4);
        assertThat(day.temporalMatch()).isEqualTo(TemporalMatch.TEMPORAL_DAY_ONLY);
        assertThat(day.matchedEventWindow()).isFalse();
    }
}
