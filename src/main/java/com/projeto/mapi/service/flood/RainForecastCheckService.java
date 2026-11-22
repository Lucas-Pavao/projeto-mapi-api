package com.projeto.mapi.service.flood;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Conferência semanal do rain_v1 contra a persistência, separando antes e depois de cada quebra de série (V43, V44). */
public interface RainForecastCheckService {

    /** Confere a semana local (segunda a segunda, America/Recife) que começa em {@code monday}. Devolve as linhas gravadas. */
    int runWeek(LocalDate monday);

    List<Map<String, Object>> latest(int limit);
}
