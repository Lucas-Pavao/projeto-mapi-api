package com.projeto.mapi.service.ingestion.validation;

/** Linha do variable_catalog usada pela validação. */
public record VariableRule(String variable, String canonicalUnit, Double min, Double max, String rangeSource,
                           String expectedResolution, String intervalConvention, String hourlyAggregation,
                           double minCompleteness) {}
