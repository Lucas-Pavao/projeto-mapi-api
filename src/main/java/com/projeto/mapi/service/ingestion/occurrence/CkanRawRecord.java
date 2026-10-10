package com.projeto.mapi.service.ingestion.occurrence;

import java.util.Map;

/** Registro bruto do CKAN: identificador estável dentro do recurso e os campos como a fonte entregou. */
public record CkanRawRecord(String recordId, Map<String, Object> fields) {}
