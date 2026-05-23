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
        private String apiUrl = "https://tabuamare.devtu.qzz.io/api/v2";
    }

    @Data
    public static class Marine {
        private String apiUrl = "https://marine-api.open-meteo.com/v1/marine";
    }
}
