package com.projeto.mapi.config;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Configuração do pipeline de ingestão (ADR 0006). Prefixo {@code mapi.ingestion}. */
@Data
@Configuration
@ConfigurationProperties(prefix = "mapi.ingestion")
public class IngestionProperties {

    /** Versão do conjunto de regras de validação gravada em cada observação e quarentena. */
    private String ruleVersion = "2026.10.4";

    /** Fallback explícito (nunca silencioso) quando o build não trouxe git.properties nem override. */
    public static final String NO_GIT = "unknown-build-without-git";

    /**
     * Override manual de {@code mapi.ingestion.code-version}. Normalmente vazio: a versão vem do
     * {@code git.properties} gerado no build (ver {@link #getCodeVersion()}).
     */
    private String codeVersion;

    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private GitProperties gitProperties;

    @Autowired(required = false)
    void injectGitProperties(GitProperties gitProperties) {
        this.gitProperties = gitProperties;
    }

    /**
     * Versão do código em uso: override explícito, senão o commit completo do {@code git.properties}
     * (sufixo {@code -dirty} se havia alterações não commitadas no build), senão {@link #NO_GIT}.
     */
    public String getCodeVersion() {
        if (codeVersion != null && !codeVersion.isBlank()) {
            return codeVersion;
        }
        if (gitProperties != null && gitProperties.getCommitId() != null && !gitProperties.getCommitId().isBlank()) {
            boolean dirty = "true".equalsIgnoreCase(gitProperties.get("dirty"));
            return gitProperties.getCommitId() + (dirty ? "-dirty" : "");
        }
        return NO_GIT;
    }

    /** Valores sentinela de "sem dado" reprovados como SENTINEL ([sem fonte]: convenção comum de agências). */
    private List<Double> sentinels = List.of(-9999.0, -999.0);

    /** Registros por lote/transação. */
    private int batchSize = 1000;

    /** Folga (dias) ao redor de [t0,t1) na pré-seleção textual do REVALIDATE; o filtro exato é por instante. */
    private int revalidateMarginDays = 3;

    /** Base da API de previsão do Open-Meteo (usada só pela verificação de fuso, para as últimas horas, com {@code past_days}). */
    private String openMeteoForecastUrl = "https://api.open-meteo.com/v1";

    /** Base da API de arquivo do Open-Meteo. */
    private String openMeteoArchiveUrl = "https://archive-api.open-meteo.com/v1";

    /** Atraso (dias) até o qual o archive costuma ter dados; o fim da janela é limitado a hoje - atraso. */
    private int openMeteoArchiveLagDays = 2;

    /** Atraso (dias) do ERA5-Land: "Daily with 5 days delay" (Open-Meteo, 2026-10-10) + 1 de folga. */
    private int era5LandLagDays = 6;

    /** Raio (m) do vínculo ocorrência-ponto quando não há linkage_config gravado. Padrão 500 m: decisão do autor, pergunta 25, 2026-10-10
     * (o config de 3 km da V6 permanece, sem apagar casamentos antigos). */
    private double occurrenceRadiusM = 500.0;

    /** Base do serviço legado público da ANA (sem credencial): inventário e série telemétrica por estação. */
    private String anaLegacyUrl = "http://telemetriaws1.ana.gov.br/ServiceANA.asmx";

    /**
     * Estações telemétricas da ANA com pluviômetro na RMR/Zona da Mata (levantadas em 2026-10-10 no inventário
     * {@code ListaEstacoesTelemetricas} com sonda de {@code DadosHidrometeorologicos}; ver F2-DADOS-07): 39187800 São Lourenço
     * da Mata II, 39370100 Engenho Maranhão (Ipojuca), 39167000 Vitória de Santo Antão Montante, 39084000 Caricé.
     * Somam-se a elas os códigos numéricos de 8 dígitos dos pluviômetros ligados aos pontos críticos.
     */
    private List<String> anaHistoricalStations = List.of("39187800", "39370100", "39167000", "39084000");
}
