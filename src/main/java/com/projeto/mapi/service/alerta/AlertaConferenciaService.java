package com.projeto.mapi.service.alerta;

import com.projeto.mapi.dto.AlertaConferenciaDTO;

import java.time.LocalDate;
import java.util.List;

/** Conferência diária do alerta (item 4.2) e relatório de atraso do primeiro dia com chuva (item 2.1). */
public interface AlertaConferenciaService {

    /** Confere um dia (UTC) para todos os pontos ativos. Devolve quantos pontos conferiu. */
    int checkDay(LocalDate dayUtc);

    /** Gera o relatório de atraso dos dias com chuva acima de 10 mm em algum ponto que ainda não têm. Devolve quantos. */
    int latencyReports();

    AlertaConferenciaDTO summary(int days);

    List<java.util.Map<String, Object>> reports();
}
