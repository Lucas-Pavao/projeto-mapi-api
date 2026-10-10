package com.projeto.mapi.service.ingestion.validation;

import com.projeto.mapi.service.ingestion.DraftValue;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.ingestion.RawRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validação PURA (sem HTTP, sem banco; ADR 0006 seção 1): converte o fuso, confere unidade e faixa e
 * devolve observações válidas ou reprovações com regra e motivo. Lacuna não reprova: valor nulo apenas
 * não gera observação (a ausência vira NULL na hora, nunca constante).
 */
public class ValidationService {

    private static final Set<String> RESOLUTIONS = Set.of("PT5M", "PT10M", "PT15M", "PT1H", "P1D", "EVENT");

    private final List<Double> sentinels;

    public ValidationService(List<Double> sentinels) {
        this.sentinels = List.copyOf(sentinels);
    }

    /**
     * @param raw      registro de origem (o {@code sourceTz} dele é só auditoria; vale o fuso efetivo)
     * @param tz       fuso efetivo da fonte hoje (source_registry)
     * @param drafts   valores extraídos do payload pelo adaptador da fonte
     * @param catalog  variable_catalog por nome
     */
    public RecordValidation validate(RawRecord raw, SourceTzRule tz, List<DraftValue> drafts,
                                     Map<String, VariableRule> catalog) {
        List<ValidObservation> valid = new ArrayList<>();
        List<Rejection> rejections = new ArrayList<>();

        if (tz == null || tz.unverified()) {
            rejections.add(new Rejection("TZ", "TZ_UNVERIFIED", detail("sourceTz", tz == null ? null : tz.tz())));
            return new RecordValidation(valid, rejections);
        }

        Instant base;
        try {
            base = TimestampNormalizer.toInstant(raw.sourceTsNorm(), tz.tz());
        } catch (TzUnverifiedException e) {
            rejections.add(new Rejection("TZ", "TZ_UNVERIFIED", detail("sourceTz", tz.tz())));
            return new RecordValidation(valid, rejections);
        } catch (TsUnparseableException e) {
            rejections.add(new Rejection("TS", "TS_UNPARSEABLE", detail("sourceTsText", raw.sourceTsText())));
            return new RecordValidation(valid, rejections);
        }

        for (DraftValue d : drafts) {
            if (d.value() == null) continue; // lacuna: não reprova, não gera observação
            VariableRule rule = catalog.get(d.variable());
            if (rule == null) {
                rejections.add(new Rejection("CATALOG", "UNKNOWN_VARIABLE", detail("variable", d.variable())));
                continue;
            }
            if (d.resolution() == null || !RESOLUTIONS.contains(d.resolution())) {
                rejections.add(new Rejection("RESOLUTION", "UNKNOWN_RESOLUTION",
                        detail("variable", d.variable(), "resolution", d.resolution())));
                continue;
            }
            double raw0 = d.value();
            if (Double.isNaN(raw0) || Double.isInfinite(raw0) || sentinels.contains(raw0)) {
                rejections.add(new Rejection("RANGE", "SENTINEL", detail("variable", d.variable(), "value", raw0)));
                continue;
            }
            Double converted = convert(raw0, d.unit(), rule.canonicalUnit());
            if (converted == null) {
                rejections.add(new Rejection("UNIT", "UNIT_MISMATCH", detail("variable", d.variable(),
                        "unit", d.unit(), "expected", rule.canonicalUnit())));
                continue;
            }
            if ((rule.min() != null && converted < rule.min()) || (rule.max() != null && converted > rule.max())) {
                rejections.add(new Rejection("RANGE", "OUT_OF_RANGE", detail("variable", d.variable(),
                        "value", converted, "min", rule.min(), "max", rule.max(), "rangeSource", rule.rangeSource())));
                continue;
            }

            Instant ts;
            Instant start;
            try {
                if ("P1D".equals(d.resolution())) {
                    start = base;
                    ts = TimestampNormalizer.endOfLocalDay(raw.sourceTsNorm(), tz.tz());
                } else if ("EVENT".equals(d.resolution()) || "INSTANT".equals(rule.intervalConvention())) {
                    ts = base;
                    start = base;
                } else {
                    ts = base;
                    start = base.minus(Duration.parse(d.resolution()));
                }
            } catch (TsUnparseableException e) {
                rejections.add(new Rejection("TS", "TS_UNPARSEABLE", detail("sourceTsText", raw.sourceTsText())));
                continue;
            }
            String hash = Hashing.sha256(Double.toString(converted) + "|" + rule.canonicalUnit() + "|" + d.qualifier());
            valid.add(new ValidObservation(d.variable(), d.resolution(), ts, start, converted,
                    rule.canonicalUnit(), d.qualifier(), hash));
        }
        return new RecordValidation(valid, rejections);
    }

    /** Converte para a unidade canônica; null se a unidade não é a esperada e não há conversão cadastrada. */
    static Double convert(double value, String unit, String canonical) {
        if (unit == null || unit.isBlank() || unit.equals(canonical)) return value;
        return switch (unit + ">" + canonical) {
            case "m/s>km/h" -> value * 3.6;
            case "Pa>hPa" -> value / 100.0;
            case "kPa>hPa" -> value * 10.0;
            case "K>°C" -> value - 273.15;
            default -> null;
        };
    }

    private static Map<String, Object> detail(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }
}
