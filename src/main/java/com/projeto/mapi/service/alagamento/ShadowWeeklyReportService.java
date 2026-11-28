package com.projeto.mapi.service.alagamento;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Relatório semanal do modo sombra (onda 16, Fase 9.4; V49). Nunca compara com rótulo de alagamento. */
public interface ShadowWeeklyReportService {

    /** Calcula e grava a semana local (segunda a segunda, America/Recife) que começa em {@code monday}. Devolve as linhas. */
    int runWeek(LocalDate monday);

    List<Map<String, Object>> latest(int weeks);
}
