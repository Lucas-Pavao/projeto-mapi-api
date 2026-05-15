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

    @Data
    public static class Navy {
        private String baseUrl;
    }

    @Data
    public static class Weather {
        private String apiUrl;
    }
}
