package com.projeto.mapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Weather weather = new Weather();
    private TabuaMare tabuamare = new TabuaMare();
    private Marine marine = new Marine();
    private Ana ana = new Ana();
    private Apac apac = new Apac();
    private Cemaden cemaden = new Cemaden();
    private Inmet inmet = new Inmet();
    private Cookie cookie = new Cookie();
    private Cors cors = new Cors();

    @Data
    public static class Weather {
        private String apiUrl;
    }

    @Data
    public static class Ana {
        private String identifier;
        private String password;
        private String authUrl = "https://www.ana.gov.br/hidrowebservice/EstacoesTelemetricas/OAUth/v1";
        private String baseUrl = "https://www.ana.gov.br/hidrowebservice/EstacoesTelemetricas/HidroinfoanaSerieTelemetricaAdotada/v1";
        private String inventoryUrl = "https://www.ana.gov.br/hidrowebservice/EstacoesTelemetricas/HidroInventarioEstacoes/v1";
        private String tipoFiltroData = "DATA_LEITURA";
        private String intervaloBusca = "HORA_16";
        private boolean enabled = true;
        private long fixedRateMs = 900000L;
        private long initialDelayMs = 15000L;

        // Descoberta dinâmica de estações (substitui a antiga lista fixa de 8 códigos, dos quais 2
        // não existiam mais no inventário ativo da ANA). Serviço legado, público e sem
        // autenticação, já usado por AnaHistoricalServiceImpl — lista todas as estações
        // telemétricas do Brasil; filtramos por UF + raio a partir de um centro (RMR por padrão).
        private String discoveryUrl = "http://telemetriaws1.ana.gov.br/ServiceANA.asmx/ListaEstacoesTelemetricas";
        private String discoveryUf = "PE";
        private double discoveryCenterLat = -8.0476;
        private double discoveryCenterLon = -34.8770;
        private double discoveryRadiusKm = 50.0;
        private long discoveryCacheTtlHours = 24L;
    }

    @Data
    public static class Apac {
        private String baseUrl = "http://dados.apac.pe.gov.br:41120";
        private String cemadenEndpoint = "cemaden";
        private String meteorologiaEndpoint = "meteorologia24h";
        private boolean cemadenRmrOnly = true;
        private boolean meteorologiaRmrOnly = false;
        private boolean enabled = true;
        private long cemadenFixedRateMs = 180000L;
        private long meteorologiaFixedRateMs = 300000L;
        private long initialDelayMs = 20000L;
    }

    /** Painel público da CEMADEN (acumulados oficiais por estação; ver CemadenAccumulationClient). */
    @Data
    public static class Cemaden {
        private String baseUrl = "https://resources.cemaden.gov.br";
        private String uf = "PE";
        /** Por quanto tempo a resposta do painel é reaproveitada (a CEMADEN atualiza a cada 10 min). */
        private long cacheTtlMs = 300000L;
    }

    /** Avisos meteorológicos severos oficiais do INMET (ADR 0024). */
    @Data
    public static class Inmet {
        private String baseUrl = "https://apiprevmet3.inmet.gov.br";
        private String activePath = "/avisos/ativos";
        /** O endpoint do INMET bloqueia/dá timeout sem um User-Agent de navegador (verificado 2026-11-16). */
        private String userAgent = "Mozilla/5.0 (compatible; MAPI/1.0)";
        private boolean enabled = true;
        private long fixedRateMs = 600_000L;
        private long initialDelayMs = 45_000L;
        /** Municípios base (Recife, Jaboatão, Olinda), somados aos municípios dos flood_points ativos. */
        private java.util.List<String> targetGeocodes = java.util.List.of("2611606", "2607901", "2609600");
        /** Previsão por município (prompt dos horizontes, bloco A; conferida em 2026-11-22). */
        private String forecastPath = "/previsao/{ibge}";
        private boolean forecastEnabled = true;
        /** A cada 3 h: a previsão muda poucas vezes por dia (docs/extratos/inmet-previsao-municipio.md). */
        private long forecastFixedRateMs = 10_800_000L;
        private long forecastInitialDelayMs = 90_000L;
        /** Pausa entre municípios: uma requisição por vez (carga educada). */
        private long forecastPauseMs = 2_000L;
    }

    @Data
    public static class TabuaMare {
        private String apiUrl = "https://tabuamare.api.br/api/v2";
    }

    @Data
    public static class Marine {
        private String apiUrl = "https://marine-api.open-meteo.com/v1/marine";
    }

    @Data
    public static class Cookie {
        private boolean secure = true;
    }

    @Data
    public static class Cors {
        // "localhost" e "127.0.0.1" são origens DIFERENTES pro navegador (o header Origin é
        // comparado literalmente) — alguns ambientes resolvem "localhost" para IPv6 (::1) antes
        // de tentar IPv4, então é comum acessar via 127.0.0.1 diretamente. Ambas ficam liberadas
        // por padrão pra não travar o CORS por causa dessa diferença.
        private java.util.List<String> allowedOrigins = java.util.List.of("http://localhost:3000", "http://127.0.0.1:3000");
    }
}
