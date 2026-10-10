package com.projeto.mapi.service.sensor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Idade (em segundos) do último dado entregue por cada fonte de coleta, exposta como gauge
 * {@code mapi.collector.last.data.age} (Prometheus: {@code mapi_collector_last_data_age_seconds},
 * label {@code source}). Os contadores existentes (processed/errors) só dizem o que aconteceu em cada
 * ciclo; não permitem alertar "a APAC não entrega dado há 30 min", que é o que importa para um sistema
 * de alerta de alagamento. Exemplo de regra: {@code mapi_collector_last_data_age_seconds > 1800}.
 *
 * "Último dado" = último ciclo em que a fonte respondeu E processou ao menos uma leitura. Enquanto
 * uma fonte ainda não entregou nada desde a subida da aplicação, a idade conta desde a subida (em vez
 * de NaN/ausente), para o alerta disparar também quando a fonte nunca funcionou.
 */
@Component
public class CollectorFreshness {

    public static final String METRIC = "mapi.collector.last.data.age";
    public static final List<String> SOURCES = List.of("ana", "apac_cemaden", "apac_meteorologia");

    private final MeterRegistry registry;
    private final Clock clock;
    private final Map<String, AtomicReference<Instant>> lastSuccess = new ConcurrentHashMap<>();

    @Autowired
    public CollectorFreshness(MeterRegistry registry) {
        this(registry, Clock.systemUTC());
    }

    CollectorFreshness(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
        SOURCES.forEach(this::register);
    }

    /** Registra que a fonte acabou de entregar dados. */
    public void markSuccess(String source) {
        register(source).set(clock.instant());
    }

    /** Idade atual do último dado da fonte, em segundos. */
    public double ageSeconds(String source) {
        AtomicReference<Instant> ref = lastSuccess.get(source);
        if (ref == null) return Double.NaN;
        return Duration.between(ref.get(), clock.instant()).toMillis() / 1000.0;
    }

    private AtomicReference<Instant> register(String source) {
        return lastSuccess.computeIfAbsent(source, s -> {
            Gauge.builder(METRIC, this, t -> t.ageSeconds(s))
                    .tag("source", s)
                    .baseUnit("seconds")
                    .description("Segundos desde o último ciclo em que a fonte entregou ao menos uma leitura")
                    .register(registry);
            return new AtomicReference<>(clock.instant());
        });
    }
}
