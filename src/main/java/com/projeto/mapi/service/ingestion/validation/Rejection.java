package com.projeto.mapi.service.ingestion.validation;

import java.util.Map;

/** Reprovação com regra e motivo gravados na quarentena. */
public record Rejection(String ruleId, String reason, Map<String, Object> detail) {}
