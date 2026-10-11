package com.projeto.mapi.service.terrain;

/** Colunas terrain_* do CSV de treino (ADR 0008): LEFT JOIN em point_terrain da execução vigente. */
public interface TerrainExportColumns {

    /** Proveniência do terreno que foi anexado ao CSV; nula se não há execução vigente. */
    record Provenance(long runId, String vrtSha256) {}

    record Appended(String csv, Provenance provenance) {}

    /** Acrescenta as colunas a cada linha do CSV (slug na primeira coluna); vazias se NO_DATA ou sem registro. */
    Appended append(String csv);
}
