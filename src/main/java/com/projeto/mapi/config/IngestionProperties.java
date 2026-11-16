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
    private String ruleVersion = "2026.11.1";

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

    /**
     * Raio máximo (m) do vínculo estação↔ponto da chuva medida (ADR 0018). Seleciona o {@code linkage_config}
     * {@code SENSOR_POINT} com esse raio; fora dele o ponto usa a reanálise, declarada.
     */
    private double sensorLinkageRadiusM = 5000.0;

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

    /** Histórico pluviométrico da APAC (formulário com exportação XLSX; REVISAO-INGESTAO-2026-11, seção 1). */
    private String apacHistUrl = "http://dados.apac.pe.gov.br:41120/boletins/historico-pluviometrico/";

    /** Pausa entre requisições à APAC histórica (ms): uma por vez, sem paralelismo. */
    private long apacHistPauseMs = 3000;

    /** Mesorregião consultada na APAC histórica. */
    private String apacHistMesorregiao = "Metropolitana de Recife";

    /**
     * Estações telemétricas da ANA com pluviômetro na RMR/Zona da Mata (levantadas em 2026-10-10 no inventário
     * {@code ListaEstacoesTelemetricas} com sonda de {@code DadosHidrometeorologicos}; ver F2-DADOS-07): 39187800 São Lourenço
     * da Mata II, 39370100 Engenho Maranhão (Ipojuca), 39167000 Vitória de Santo Antão Montante, 39084000 Caricé.
     * Somam-se a elas os códigos numéricos de 8 dígitos dos pluviômetros ligados aos pontos críticos.
     */
    private List<String> anaHistoricalStations = List.of("39187800", "39370100", "39167000", "39084000");

    /** Cemaden pela Plataforma de Entrega de Dados (ADR 0021). Prefixo {@code mapi.ingestion.cemaden-ped}. */
    private CemadenPed cemadenPed = new CemadenPed();

    @Data
    public static class CemadenPed {
        /** Base dos webservices da PED (swagger em https://sws.cemaden.gov.br/PED/api/ui/). */
        private String baseUrl = "https://sws.cemaden.gov.br/PED/rest";
        /** Emissão do token JWT (fora do swagger da PED; verificado em 2026-11-09). */
        private String tokenUrl = "https://sgaa.cemaden.gov.br/SGAA/rest/controle-token/tokens";
        /** Credenciais da conta na PED; vêm do ambiente ({@code CEMADEN_PED_EMAIL}, {@code CEMADEN_PED_SENHA}). */
        private String email;
        private String password;
        /**
         * Municípios (IBGE) cujo cadastro é lido: Recife, Jaboatão dos Guararapes, Olinda e Camaragibe, os quatro com
         * estação a até 10 km de algum ponto crítico (levantamento de 2026-11-09, docs/extratos/cemaden-ped.md).
         */
        private List<String> ibgeCodes = List.of("2611606", "2607901", "2609600", "2603454");
        /**
         * Estações baixadas: as que ficam a até este raio (m) de algum ponto ativo. Maior que o raio do vínculo (5 km)
         * para que a checagem espacial tenha vizinhos dos dois lados ({@code [sem fonte]}, ADR 0022).
         */
        private double stationSelectionRadiusM = 10000.0;
        /** Janela por requisição; a {@code dados_pcd} aceita no máximo 30 dias. */
        private int windowDays = 29;
        /**
         * Quanto antes do início da janela a requisição começa, para conhecer a leitura anterior à primeira da janela.
         * Maior que o bloco máximo (60 min): sem leitura anterior nesse intervalo, o bloco é de duração > 60 min.
         */
        private int lookbackMinutes = 70;
        /** Pula as janelas já gravadas em {@code ped_fetch_window} (carga retomável). */
        private boolean resume = true;
        /**
         * Intervalo mínimo entre requisições. A PED limita usuário externo a 12 por minuto e bloqueia quem passa;
         * 6 s = 10 por minuto, com folga, e sem rajada na virada do minuto.
         */
        private long minIntervalMs = 6000;
        /** Tentativas para erro transitório (rede, 429, 5xx) antes de falhar o run (que pode ser retomado). */
        private int maxAttempts = 4;
        /** Espera base entre tentativas (dobra a cada uma). */
        private long retryBackoffMs = 30000;
        /**
         * Códigos de {@code qualificacao} que vão para a quarentena ({@code QUALIFIER / SUSPECT_QUALIFIER}). Decisão do
         * Lucas em 2026-11-09, antes da resposta do Cemaden: {@code 0002} aparece em 70 blocos nos 5 anos, todos acima
         * de 25 mm (até 255,5 mm, valores repetidos idênticos); com {@code 0004} o maior bloco é 26,9 mm
         * (docs/extratos/cemaden-ped.md). Reversível por REVALIDATE se a resposta mudar.
         */
        private List<String> suspectQualifiers = List.of("0002");
        /**
         * Acumulados recentes medidos ({@code /pcds-acum/acumulados-recentes}, ADR 0023): uma requisição por município a
         * cada ciclo, pelo mesmo cliente e o mesmo limite de 10 por minuto da carga. A PED atualiza a cada 10 min.
         */
        private boolean accumulationEnabled = true;
        private long accumulationRefreshMs = 600_000;
        private long accumulationInitialDelayMs = 30_000;
        /** Leitura mais velha que isto sai marcada {@code stale} (dois ciclos sem renovar). */
        private long accumulationStaleAfterMs = 1_800_000;
        /**
         * Carga periódica (incremental) da série de 10 min: a cada ciclo baixa de (última leitura gravada − sobreposição)
         * até a hora cheia atual e recalcula a chuva dos pontos dos meses tocados (ADR 0021, emenda de 2026-11-16).
         */
        private boolean incrementalEnabled = true;
        private long incrementalDelayMs = 3_600_000;
        private long incrementalInitialDelayMs = 300_000;
        /** Sobreposição com o que já está gravado: a PED pode completar blocos atrasados ({@code [sem fonte]}). */
        private int incrementalOverlapHours = 6;
        /** Banco sem PED recente: a carga periódica não volta mais que isto (a histórica é pelo endpoint de runs). */
        private int incrementalMaxCatchUpDays = 60;
    }
}
