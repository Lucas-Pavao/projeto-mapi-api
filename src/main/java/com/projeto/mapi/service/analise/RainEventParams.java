package com.projeto.mapi.service.analise;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parâmetros de uma extração de eventos de chuva (ADR 0017). Todos entram no manifesto e na identidade do evento.
 *
 * @param tideSillM soleira da galeria no referencial da maré (NR DHN, m); nula = não informada (ADR 0009: Δ entre MDT e
 *                  NR não fechado, então não há soleira padrão)
 */
public record RainEventParams(String slug, Instant from, Instant to, RainSource source, int dryGapHours,
                              double minEventMm, RainEventClassifier.Classification classification,
                              double idfThresholdMmH, double capacityMmH, double runoffCoefficient, Double tideSillM,
                              double springRangeM) {

    /** Versão do método; muda quando a definição de evento ou o cálculo de uma coluna muda. */
    public static final String METHOD_VERSION = "eventos-chuva-v2";

    /** Padrões (ADR 0017; origem de cada número em docs/EVENTOS-DE-CHUVA.md). */
    public static final int DEFAULT_DRY_GAP_HOURS = 6;
    public static final double DEFAULT_MIN_EVENT_MM = 1.0;
    public static final double DEFAULT_IDF_THRESHOLD_MM_H = 50.3;
    public static final double DEFAULT_CAPACITY_MM_H = 60.1;
    public static final double DEFAULT_RUNOFF_COEFFICIENT = 0.86;
    public static final double DEFAULT_SPRING_RANGE_M = 1.7;

    /** Fonte da chuva: chave do pipeline, resolução nativa e leituras esperadas por hora. */
    public enum RainSource {
        ANA("ANA_SOAP", "PT15M", 4),
        /** Blocos de 10/60 min somados na hora (V22); a view declara 1 leitura esperada por hora. */
        CEMADEN("CEMADEN", "EVENT", 1),
        OPEN_METEO_ARCHIVE("OPEN_METEO_ARCHIVE", "PT1H", 1);

        public final String pipelineSource;
        public final String resolution;
        public final int readingsPerHour;

        RainSource(String pipelineSource, String resolution, int readingsPerHour) {
            this.pipelineSource = pipelineSource;
            this.resolution = resolution;
            this.readingsPerHour = readingsPerHour;
        }

        public boolean subHourly() { return readingsPerHour > 1; }

        /** Pluviômetro (sujeito ao raio do ADR 0018) ou reanálise. */
        public boolean measured() { return this != OPEN_METEO_ARCHIVE; }
    }

    /** Regras que definem o evento e as colunas derivadas, em ordem fixa (entra no hash do eventId). */
    public Map<String, Object> rulesMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("methodVersion", METHOD_VERSION);
        m.put("dryGapHours", dryGapHours);
        m.put("minEventMm", minEventMm);
        m.put("classification", classification.name());
        m.put("idfThresholdMmH", idfThresholdMmH);
        m.put("typeBoundsMmH", new java.util.TreeMap<>(RainEventClassifier.boundsForManifest(classification, idfThresholdMmH)));
        m.put("capacityMmH", capacityMmH);
        m.put("runoffCoefficient", runoffCoefficient);
        m.put("tideSillM", tideSillM);
        m.put("springRangeM", springRangeM);
        return m;
    }

    /** Texto canônico das regras (para o hash do eventId). */
    public String rulesKey() {
        StringBuilder sb = new StringBuilder();
        rulesMap().forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        return sb.toString();
    }
}
