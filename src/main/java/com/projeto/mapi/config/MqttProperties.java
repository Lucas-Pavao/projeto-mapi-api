package com.projeto.mapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {
    private Broker broker = new Broker();
    private Client client = new Client();
    private String topic;

    @Data
    public static class Broker {
        private String url;
    }

    @Data
    public static class Client {
        private String id;
    }
}
