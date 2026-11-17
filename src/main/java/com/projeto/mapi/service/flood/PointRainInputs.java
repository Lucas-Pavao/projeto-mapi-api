package com.projeto.mapi.service.flood;

import java.time.Instant;
import java.util.List;

/**
 * Entradas de chuva da previsão ao vivo (ADR 0007, emenda 3), montadas com a regra do treino (ADR 0022): por hora, o
 * maior valor entre as estações válidas a até 5 km. Valor nulo = ausente (lacuna), nunca zero.
 *
 * @param source           {@code MEASURED} (série medida) ou {@code REANALYSIS_FALLBACK} (Open-Meteo, só se ligado)
 * @param hourEndUtc       fim da hora de referência (a "chuva agora" é a chuva dessa hora)
 * @param current          chuva da hora de referência (mm)
 * @param values           uma entrada por feature de chuva, com as estações que forneceram as horas
 * @param latestReadingUtc leitura mais recente entre as estações válidas do raio
 * @param nValid           estações válidas no raio na hora de referência
 */
public record PointRainInputs(String source, Instant hourEndUtc, Double current, Double acc3h, Double acc6h,
                              Double acc12h, Double acc24h, List<Value> values, Instant latestReadingUtc, int nValid,
                              String rainTier, Double rainConfidence, List<String> flags) {

    public static final String MEASURED = "MEASURED";
    public static final String REANALYSIS_FALLBACK = "REANALYSIS_FALLBACK";

    public PointRainInputs(String source, Instant hourEndUtc, Double current, Double acc3h, Double acc6h,
                           Double acc12h, Double acc24h, List<Value> values, Instant latestReadingUtc, int nValid) {
        this(source, hourEndUtc, current, acc3h, acc6h, acc12h, acc24h, values, latestReadingUtc, nValid,
                null, null, List.of());
    }

    /** {@code hoursWithValue < hours} explica o valor ausente; {@code stations} são as que deram o maior valor de cada hora. */
    public record Value(String featureId, Double valueMm, int hours, int hoursWithValue, List<Station> stations) {}

    public record Station(String stationId, String name, String source) {}

    /** Features de chuva sem valor, na ordem do contrato. */
    public List<String> absent() {
        return values.stream().filter(v -> v.valueMm() == null).map(Value::featureId).toList();
    }

    /**
     * Regra de degradação (ADR 0007, emenda 3): sem a chuva da hora de referência E sem o acumulado de 3 h, a previsão
     * vira UNKNOWN em vez de devolver um número.
     */
    public boolean insufficient() {
        return current == null && acc3h == null;
    }
}
