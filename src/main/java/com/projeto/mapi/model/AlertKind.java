package com.projeto.mapi.model;

public enum AlertKind {
    RISK_LEVEL, RIVER_STAGE, TIDE_ABOVE, RAIN_RATE, SENSOR_STALE, PREDICTION_UNAVAILABLE,
    /** Fonte de dados sem leitura aceita dentro da idade máxima (V29); alerta do sistema, sem regra. */
    SOURCE_STALE
}
