package com.projeto.mapi.service.alerta;

import com.projeto.mapi.dto.AlertaLatenciaDTO;
import com.projeto.mapi.dto.AlertaPontoDTO;
import com.projeto.mapi.dto.AlertaTransicaoDTO;

import java.util.List;

/** Leitura do alerta por ponto. Não roda ciclo nem chama a IA. */
public interface AlertaQueryService {

    List<AlertaPontoDTO> current();

    List<AlertaTransicaoDTO> transitions(String slug, int hours, int limit);

    AlertaLatenciaDTO latency(int days);

    boolean shadow();
}
