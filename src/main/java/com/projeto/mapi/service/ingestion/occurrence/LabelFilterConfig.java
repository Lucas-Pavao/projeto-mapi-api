package com.projeto.mapi.service.ingestion.occurrence;

import java.util.List;

/**
 * Filtro de rótulo gravado em {@code label_filter} (ADR 0006 seção 6). {@code acceptedCodes} são códigos Cobrade;
 * {@code acceptedTexts} é o filtro por texto usado quando o dado não traz o código (caso do CKAN do Recife).
 */
public record LabelFilterConfig(long id, String name, List<String> acceptedCodes, List<String> acceptedTexts) {}
