package com.projeto.mapi.dto;

import java.util.List;

/** Resultado de POST /api/admin/terrain/runs. {@code created = false}: o mesmo conteúdo já tinha sido carregado. */
public record TerrainLoadResultDTO(long runId, boolean created, boolean current, int pointsOk, int pointsNoData,
                                   int curveRows, List<String> slugsNotFound, String inputSha256) {}
