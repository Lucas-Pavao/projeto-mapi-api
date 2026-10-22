package com.projeto.mapi.dto;

import java.util.List;

/** Página genérica do contrato (PageMeta em docs/openapi/redesign-telas.yaml). */
public record PageResponseDTO<T>(int page, int size, long totalElements, List<T> content) {
}
