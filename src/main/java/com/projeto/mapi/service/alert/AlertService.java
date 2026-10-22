package com.projeto.mapi.service.alert;

import com.projeto.mapi.dto.AlertDTO;
import com.projeto.mapi.dto.AlertRuleDTO;
import com.projeto.mapi.dto.AlertRuleInputDTO;
import com.projeto.mapi.dto.PageResponseDTO;

import java.time.Instant;
import java.util.List;

/** Consulta de alertas, reconhecimento e CRUD de regras (OPERADOR). */
public interface AlertService {

    /** {@code status}: OPEN, ACKNOWLEDGED ou ALL. {@code from}/{@code to} (opcionais) filtram por triggeredAt. */
    PageResponseDTO<AlertDTO> list(String status, Instant from, Instant to, int page, int size);

    /** Idempotente: reconhecer de novo devolve o alerta como está, sem trocar quem reconheceu. */
    AlertDTO acknowledge(Long id, String username);

    List<AlertRuleDTO> listRules();

    AlertRuleDTO createRule(AlertRuleInputDTO input, String username);

    AlertRuleDTO updateRule(Long id, AlertRuleInputDTO input);

    void deleteRule(Long id);
}
