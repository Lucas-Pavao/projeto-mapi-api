package com.projeto.mapi.util;

import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.model.SensorData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class MqttRealTimeValidator implements CommandLineRunner {

    private final SensorDataRepository sensorDataRepository;

    @Override
    public void run(String... args) {
        log.info("=== INICIANDO VALIDAÇÃO DE DADOS MQTT EM TEMPO REAL ===");
        
        List<String> ids = sensorDataRepository.findDistinctSensorIds();
        log.info("Sensores detectados no banco: {}", ids);

        for (String id : ids) {
            sensorDataRepository.findFirstBySensorIdOrderByTimestampDesc(id).ifPresent(data -> {
                log.info("Última leitura de [{}]: Valor={}, Unidade={}, Timestamp={}, Fonte={}", 
                    id, data.getValue(), data.getUnit(), data.getTimestamp(), data.getSource());
            });
        }
        
        long totalRecords = sensorDataRepository.count();
        log.info("Total de registros de sensores no banco: {}", totalRecords);
        log.info("======================================================");
    }
}
