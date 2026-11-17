package com.projeto.mapi.dto;

import java.time.LocalDate;
import java.util.List;

/** Conferência diária do alerta nos últimos dias (item 4.2): dia × ponto e o agregado. */
public record AlertaConferenciaDTO(int days, int forteEvents, int forteWarned, Double medianLeadMinutes,
                                   double falsePerEvent, double semDadoFraction, int floodEpisodes,
                                   List<Row> rows, String note) {

    public record Row(LocalDate day, String slug, int forteEvents, int forteWarned, List<Double> leadMinutes,
                      int alertEpisodes, int falseEpisodes, double semDadoMinutes, double evaluatedMinutes,
                      List<java.util.Map<String, Object>> floodEpisodes, boolean shadow) {}
}
