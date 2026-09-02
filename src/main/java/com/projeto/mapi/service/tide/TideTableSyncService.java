package com.projeto.mapi.service.tide;

import com.projeto.mapi.dto.TideSyncSummaryDTO;

/**
 * Popula a tábua de maré LOCAL (tide_tables/month_data/day_data/hour_data) a partir da TabuaMare
 * API (dado oficialmente atribuído à DHN — Diretoria de Hidrografia e Navegação da Marinha do
 * Brasil, ver campo "data_collection_institution" na resposta da API). Existe desde sempre no
 * schema/entidades do projeto, mas nunca foi de fato populada — todo lookup de maré caía sempre
 * na API externa, ciclo após ciclo, o que gerou rate-limit (429) em produção assim que a coleta de
 * sensores passou a processar dezenas de leituras por ciclo. Sincronizar uma vez por ano (12
 * chamadas por porto: a API já devolve o mês inteiro numa única requisição) elimina praticamente
 * toda a dependência de chamadas externas em tempo real.
 */
public interface TideTableSyncService {
    /**
     * Sincroniza o ano informado para os portos mais próximos de cada ponto de monitoramento
     * cadastrado (FloodPoint). Sobrescreve dados já existentes para o mesmo porto/ano (idempotente).
     */
    TideSyncSummaryDTO syncYear(int year);

    /**
     * Sincroniza o ano corrente apenas se ainda não houver nenhum dado local para ele — usado pelo
     * job agendado de auto-recuperação (ver TideTableSyncTask).
     */
    TideSyncSummaryDTO syncCurrentYearIfMissing();
}
