package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Estação canônica: uma por estação física, com os identificadores de origem como apelidos (ADR 0023). */
@Schema(description = "Estação física única. Os apelidos (SOURCE:station_id) são os códigos com que ela chega por cada fonte.")
public record CanonicalStationDTO(
        @Schema(description = "Código da estação na PED quando existe; senão, o da coleta ao vivo") String canonicalId,
        String name,
        String municipality,
        @Schema(description = "Código IBGE do município (7 dígitos), derivado do código da estação") String ibgeCode,
        double lat,
        double lon,
        @Schema(description = "Fonte que deu nome e coordenada: CEMADEN_PED ou CEMADEN") String primarySource,
        @Schema(description = "Identificadores de origem, no formato SOURCE:station_id") List<String> aliases) {
}
