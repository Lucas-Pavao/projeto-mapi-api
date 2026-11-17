package com.projeto.mapi.dto;

import java.time.Instant;
import java.util.List;

/** Atraso do caminho do alerta por trecho (item 2.1), em segundos, separando hora com chuva de hora seca. */
public record AlertaLatenciaDTO(Instant from, Instant to, int budgetP90Minutes, List<Segment> segments,
                                Boolean withinBudgetWet, Boolean withinBudgetDry, String note) {

    public record Segment(String segment, boolean wetHour, long n, Double medianS, Double p90S, Double maxS) {}
}
