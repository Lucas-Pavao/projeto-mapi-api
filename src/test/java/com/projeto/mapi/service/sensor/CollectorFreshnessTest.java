package com.projeto.mapi.service.sensor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorFreshnessTest {

    /** Relógio controlável: o teste avança o tempo sem dormir. */
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2025-01-01T12:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void gaugeExpostoParaCadaFonteEIdadeCrescComOTempo() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MutableClock clock = new MutableClock();
        new CollectorFreshness(registry, clock);

        for (String source : CollectorFreshness.SOURCES) {
            assertNotNull(registry.find(CollectorFreshness.METRIC).tag("source", source).gauge(), source);
        }

        clock.advance(Duration.ofMinutes(10));
        assertEquals(600.0, registry.get(CollectorFreshness.METRIC).tag("source", "ana").gauge().value(), 0.001);
    }

    @Test
    void markSuccessZeraSoAIdadeDaFonteInformada() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MutableClock clock = new MutableClock();
        CollectorFreshness freshness = new CollectorFreshness(registry, clock);

        clock.advance(Duration.ofMinutes(30));
        freshness.markSuccess("apac_cemaden");
        clock.advance(Duration.ofSeconds(45));

        assertEquals(45.0, freshness.ageSeconds("apac_cemaden"), 0.001);
        assertEquals(1845.0, freshness.ageSeconds("ana"), 0.001);
        assertEquals(45.0, registry.get(CollectorFreshness.METRIC).tag("source", "apac_cemaden").gauge().value(), 0.001);
    }

    @Test
    void fonteDesconhecidaViraGaugeNoPrimeiroSucesso() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CollectorFreshness freshness = new CollectorFreshness(registry, new MutableClock());
        assertTrue(Double.isNaN(freshness.ageSeconds("nova")));
        freshness.markSuccess("nova");
        assertEquals(0.0, registry.get(CollectorFreshness.METRIC).tag("source", "nova").gauge().value(), 0.001);
    }
}
