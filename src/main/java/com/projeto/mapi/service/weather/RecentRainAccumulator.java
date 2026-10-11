package com.projeto.mapi.service.weather;

import com.projeto.mapi.dto.HourlyPrecipitationDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Soma a chuva horária recente do Open-Meteo forecast em janelas de N horas que terminam na última hora já
 * fechada até {@code now} (mesmo relógio local da resposta). Devolve {@code null} se faltar qualquer hora da
 * janela: acumulado incompleto não vira número.
 */
public final class RecentRainAccumulator {

    private RecentRainAccumulator() {}

    public static Double sumLastHours(HourlyPrecipitationDTO hourly, LocalDateTime now, int hours) {
        if (hourly == null || hourly.time() == null || hourly.precipitation() == null || now == null) return null;
        List<Double> closed = new ArrayList<>();
        int n = Math.min(hourly.time().size(), hourly.precipitation().size());
        for (int i = 0; i < n; i++) {
            LocalDateTime t;
            try {
                t = LocalDateTime.parse(hourly.time().get(i));
            } catch (RuntimeException e) {
                return null;
            }
            if (!t.isAfter(now)) closed.add(hourly.precipitation().get(i));
        }
        if (closed.size() < hours) return null;
        double sum = 0.0;
        for (Double v : closed.subList(closed.size() - hours, closed.size())) {
            if (v == null) return null;
            sum += v;
        }
        return sum;
    }
}
