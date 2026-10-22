package com.projeto.mapi.service.report;

import com.projeto.mapi.dto.FloodReportDTO;
import com.projeto.mapi.dto.FloodReportInputDTO;
import com.projeto.mapi.dto.FloodReportReviewDTO;
import com.projeto.mapi.dto.PageResponseDTO;

/** Fila de relatos de alagamento. Nunca escreve em {@code flood_occurrence} nem nos rótulos da avaliação. */
public interface FloodReportService {

    /** Cria o relato como PENDING, ligado ao ponto ativo mais próximo (até 3 km), se houver. */
    FloodReportDTO submit(FloodReportInputDTO input, String username);

    /** {@code status} nulo lista todos. Mais recentes primeiro. */
    PageResponseDTO<FloodReportDTO> list(String status, int page, int size);

    /** CONFIRMED cria um FloodEvent do ponto informado; DISCARDED guarda o motivo. 409 se já revisado. */
    FloodReportDTO review(Long id, FloodReportReviewDTO review, String username);
}
