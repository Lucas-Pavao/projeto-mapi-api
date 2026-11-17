package com.projeto.mapi.service.alerta;

/** Campo {@code rain_alert}: depende da chuva, não do lugar. Ordem de gravidade: NENHUM < ATENCAO < ALERTA. */
public enum RainAlertLevel {
    NENHUM, ATENCAO, ALERTA, SEM_DADO;

    public boolean on() {
        return this == ATENCAO || this == ALERTA;
    }
}
