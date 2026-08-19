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
        private java.util.List<String> allowedOrigins = java.util.List.of("http://localhost:3000");
    }
}
