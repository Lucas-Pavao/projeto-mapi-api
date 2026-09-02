package com.projeto.mapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Navy navy = new Navy();
    private Weather weather = new Weather();
    private TabuaMare tabuamare = new TabuaMare();
    private Marine marine = new Marine();
    private Ana ana = new Ana();
    private Apac apac = new Apac();
    private Cookie cookie = new Cookie();
    private Cors cors = new Cors();

    @Data
    public static class Navy {
        private String baseUrl;
    }

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
