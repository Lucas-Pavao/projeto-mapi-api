package com.projeto.mapi.service.analise;

import java.time.LocalDate;
import java.util.Map;

/**
 * Classificação do evento por tipo e da estação do ano (ADR 0017). Lógica pura.
 *
 * <p>A intensidade classificada é o pico horário ({@code maxHourlyMmH}), comparável com a IDF de 60 min.
 * <ul>
 *   <li>{@link Classification#IDF}: fração do limiar local {@code idfThresholdMmH} (padrão 50,3 mm/h = i(TR 2, 60 min),
 *       Silva e Araújo 2013, {@code docs/PARAMETROS-MODELO-FISICO.md} linha 6). FORTE a partir de 0,5 do limiar
 *       (menor nível de sensibilidade testado por Fonseca Neto et al. 2026, [E3]); MÉDIA a partir de 0,1 do limiar
 *       (escolha metodológica: ~5 mm/h com o limiar padrão, alinhado à faixa meteorológica).</li>
 *   <li>{@link Classification#METEOROLOGICA}: faixas fixas, FRACA &lt; 5, MÉDIA 5 a &lt; 25, FORTE &ge; 25 mm/h
 *       (convenção usual de boletim; "forte" e "muito forte" fundidas em FORTE) [sem fonte normativa citada].</li>
 * </ul>
 */
public final class RainEventClassifier {

    public enum Type { FRACA, MEDIA, FORTE }

    public enum Classification { IDF, METEOROLOGICA }

    /** Frações do limiar IDF (MÉDIA, FORTE). */
    public static final double IDF_MEDIA_FRACTION = 0.1;
    public static final double IDF_FORTE_FRACTION = 0.5;
    /** Faixas meteorológicas (mm/h): MÉDIA a partir de, FORTE a partir de. */
    public static final double MET_MEDIA_MM_H = 5.0;
    public static final double MET_FORTE_MM_H = 25.0;

    private RainEventClassifier() {
    }

    public static Type classify(Classification c, double maxHourlyMmH, double idfThresholdMmH) {
        double[] b = bounds(c, idfThresholdMmH);
        if (maxHourlyMmH >= b[1]) return Type.FORTE;
        if (maxHourlyMmH >= b[0]) return Type.MEDIA;
        return Type.FRACA;
    }

    /** Limites efetivos em mm/h: {MÉDIA a partir de, FORTE a partir de}. */
    public static double[] bounds(Classification c, double idfThresholdMmH) {
        return switch (c) {
            case IDF -> new double[]{IDF_MEDIA_FRACTION * idfThresholdMmH, IDF_FORTE_FRACTION * idfThresholdMmH};
            case METEOROLOGICA -> new double[]{MET_MEDIA_MM_H, MET_FORTE_MM_H};
        };
    }

    /** Limites efetivos, para o manifesto. */
    public static Map<String, Double> boundsForManifest(Classification c, double idfThresholdMmH) {
        double[] b = bounds(c, idfThresholdMmH);
        return Map.of("MEDIA_fromMmH", b[0], "FORTE_fromMmH", b[1]);
    }

    /** Estação meteorológica do Hemisfério Sul por trimestre civil (DJF, MAM, JJA, SON), na data local. */
    public static String season(LocalDate localDate) {
        return switch (localDate.getMonthValue()) {
            case 12, 1, 2 -> "VERAO";
            case 3, 4, 5 -> "OUTONO";
            case 6, 7, 8 -> "INVERNO";
            default -> "PRIMAVERA";
        };
    }
}
