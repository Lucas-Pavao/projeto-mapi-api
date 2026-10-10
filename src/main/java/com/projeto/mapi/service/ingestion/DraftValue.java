package com.projeto.mapi.service.ingestion;

/** Valor extraído de um registro bruto, ainda sem fuso nem validação. {@code unit} nulo = canônica. */
public record DraftValue(String variable, String resolution, Double value, String unit, String qualifier) {}
