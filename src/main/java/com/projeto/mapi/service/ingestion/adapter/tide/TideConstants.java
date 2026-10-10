package com.projeto.mapi.service.ingestion.adapter.tide;

/** Constantes da maré prevista no Porto do Recife (DHN, carta 902, id TabuaMare {@code pe02}). */
public final class TideConstants {

    /** Identificador da estação na fonte (porto da TabuaMare). */
    public static final String STATION = "pe02";

    /** Altura referida ao Nível de Redução da DHN; NÃO convertida ao datum do MDT (ADR 0009). */
    public static final String VERTICAL_REFERENCE = "NR_DHN_pe02";

    /** Fuso fixo da tábua (hora local UTC-3, sem horário de verão), como o pipeline o declara. */
    public static final String DECLARED_TZ = "UTC-03:00";

    /** Extremos (preamar/baixa-mar), resolução EVENT. */
    public static final String VARIABLE_EXTREME = "tide_height";

    /** Série horária interpolada, resolução PT1H, instante exato da hora. */
    public static final String VARIABLE_HOURLY = "tide_height_hourly";

    public static final String PREAMAR = "PREAMAR";
    public static final String BAIXA_MAR = "BAIXA_MAR";

    private TideConstants() {}
}
