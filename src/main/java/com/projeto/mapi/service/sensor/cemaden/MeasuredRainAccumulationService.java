package com.projeto.mapi.service.sensor.cemaden;

import com.projeto.mapi.dto.MeasuredRainAccumulationsDTO;

/** Acumulados de chuva medidos pelo pluviômetro do Cemaden (PED), por estação canônica (ADR 0023). */
public interface MeasuredRainAccumulationService {

    /** Última leitura em cache; {@link com.projeto.mapi.exception.ServiceUnavailableException} se ainda não houve nenhuma. */
    MeasuredRainAccumulationsDTO current();

    /** Busca na PED, município a município, e troca o cache. Roda fora da requisição HTTP (agendado). */
    void refresh();

    /** Entrada do agendador: sem credencial da PED ou desligado, não faz nada; nunca sobrepõe dois ciclos. */
    void scheduledRefresh();
}
