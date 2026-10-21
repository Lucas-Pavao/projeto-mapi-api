package com.projeto.mapi.service.desempenho;

import com.projeto.mapi.dto.BucketSimulationDTO;
import com.projeto.mapi.dto.BucketSimulationRequestDTO;

/** Simulação do balde M1a para um ponto: monta a curva do terreno e repassa para a IA. Não grava nada. */
public interface BucketSimulationService {

    /**
     * 404 se o ponto não existe; 422 se não há terreno (ex.: Jaboatão) ou f/curveScale saem da faixa; 503 se a IA falha.
     */
    BucketSimulationDTO simulate(BucketSimulationRequestDTO request);
}
