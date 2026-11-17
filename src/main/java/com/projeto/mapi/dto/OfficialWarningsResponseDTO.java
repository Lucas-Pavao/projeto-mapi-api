package com.projeto.mapi.dto;

import java.time.Instant;
import java.util.List;

/** Resposta de avisos meteorológicos oficiais vigentes (ADR 0024). */
public record OfficialWarningsResponseDTO(
        String source,
        String attribution,
        String emissionNote,
        Instant referenceTimeUtc,
        List<OfficialWarningDTO> warnings
) {
    public static final String SOURCE_LABEL = "INMET";
    public static final String ATTRIBUTION = "INMET — Instituto Nacional de Meteorologia (https://avisos.inmet.gov.br)";
    public static final String EMISSION_NOTE =
            "firstSeenAt é proxy posterior à emissão real: a API pública do INMET não publica hora de emissão.";
}
