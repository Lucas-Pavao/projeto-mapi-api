package com.projeto.mapi.service.ingestion;

import com.projeto.mapi.dto.IngestionSourceStatusDTO;
import com.projeto.mapi.dto.PageResponseDTO;

import java.util.List;

/** Consultas de saúde da ingestão para a tela "Saúde dos dados" (ADMIN). */
public interface IngestionAdminService {

    PageResponseDTO<IngestionRunView> listRuns(String source, String status, int page, int size);

    /**
     * Status por fonte, nesta ordem: IDLE (nunca rodou), FAILING (último run FAILED), DELAYED (sem sucesso há mais
     * de 2x o intervalo esperado), QUARANTINED (último run só quarentenou) ou OK.
     */
    List<IngestionSourceStatusDTO> sources();
}
