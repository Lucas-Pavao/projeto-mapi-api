package com.projeto.mapi.config;

import com.projeto.mapi.service.SensorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MqttConfig {

    private final MqttProperties mqttProperties;
    private final SensorService sensorService;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] { mqttProperties.getBroker().getUrl() });
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inbound() {
        // Gerar um ID único para evitar conflitos no broker público
        String uniqueClientId = mqttProperties.getClient().getId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Garantir que o tópico tenha o wildcard se for destinado a múltiplos sensores
        String effectiveTopic = mqttProperties.getTopic();
        if (!effectiveTopic.contains("#") && !effectiveTopic.contains("+") && !effectiveTopic.endsWith("/")) {
            // Se for apenas um prefixo sem wildcard, talvez devesse ter um
            log.warn("O tópico MQTT '{}' não contém wildcards (# ou +). Pode não receber mensagens de sub-tópicos.", effectiveTopic);
        }
        
        log.info("Iniciando adaptador MQTT no broker '{}' tópico '{}' com Client ID: {}", mqttProperties.getBroker().getUrl(), effectiveTopic, uniqueClientId);
        
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(uniqueClientId, mqttClientFactory(), effectiveTopic);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler handler() {
        return message -> {
            String payload = message.getPayload().toString();
            String topic = message.getHeaders().get("mqtt_receivedTopic", String.class);
            log.info("Mensagem recebida do MQTT no tópico '{}': {}", topic, payload);
            sensorService.processSensorMessage(payload);
        };
    }

    @Bean
    public MessageHandler mqttErrorHandler() {
        return message -> {
            log.error("Erro no adaptador MQTT: {}", message.getPayload());
        };
    }
}
