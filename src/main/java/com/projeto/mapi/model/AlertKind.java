package com.projeto.mapi.model;

public enum AlertKind {
    RISK_LEVEL, RIVER_STAGE, TIDE_ABOVE, RAIN_RATE, SENSOR_STALE, PREDICTION_UNAVAILABLE,
    /** Fonte de dados sem leitura aceita dentro da idade máxima (V29); alerta do sistema, sem regra. */
    SOURCE_STALE,
    /** Conferência semanal do rain_v1 pior que o teste (V44); alerta do sistema, sem regra. */
    MODEL_CHECK
}
