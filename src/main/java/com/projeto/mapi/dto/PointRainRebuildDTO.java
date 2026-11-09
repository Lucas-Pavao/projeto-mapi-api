package com.projeto.mapi.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Resultado da reconstrução da chuva por ponto (ADR 0022): contagens da checagem mensal, cobertura e regras usadas. */
public record PointRainRebuildDTO(Instant from, Instant to, int stations, int months, int checks,
                                  Map<String, Integer> byStatus, Map<String, Integer> failReasons, int pointHours,
                                  Map<String, Double> pointCoverage, long linkageConfigId, Map<String, Object> rules,
                                  List<String> notes) {}
