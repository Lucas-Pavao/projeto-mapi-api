package com.projeto.mapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Configuração do pipeline de ingestão (ADR 0006). Prefixo {@code mapi.ingestion}. */
@Data
@Configuration
@ConfigurationProperties(prefix = "mapi.ingestion")
public class IngestionProperties {

    /** Versão do conjunto de regras de validação gravada em cada observação e quarentena. */
    private String ruleVersion = "2026.10.1";

    /** Commit do backend (variável de build GIT_COMMIT); "unknown" quando não informado. */
    private String codeVersion = "unknown";

    /** Valores sentinela de "sem dado" reprovados como SENTINEL ([sem fonte]: convenção comum de agências). */
    private List<Double> sentinels = List.of(-9999.0, -999.0);

    /** Registros por lote/transação. */
    private int batchSize = 1000;

    /** Folga (dias) ao redor de [t0,t1) na pré-seleção textual do REVALIDATE; o filtro exato é por instante. */
    private int revalidateMarginDays = 3;

    /** Base da API de arquivo do Open-Meteo. */
    private String openMeteoArchiveUrl = "https://archive-api.open-meteo.com/v1";

    /** Atraso (dias) até o qual o archive costuma ter dados; o fim da janela é limitado a hoje - atraso. */
    private int openMeteoArchiveLagDays = 2;

    /** Raio (m) do vínculo ocorrência-ponto quando não há linkage_config gravado. */
    private double occurrenceRadiusM = 3000.0;
}
