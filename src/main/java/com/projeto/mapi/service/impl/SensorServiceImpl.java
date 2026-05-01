package com.projeto.mapi.service.impl;

import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.service.SensorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SensorServiceImpl implements SensorService {
    private final SensorDataRepository sensorDataRepository;

    @Override
    @Transactional
    public void processSensorMessage(String payload) {
        log.info("Received MQTT message: {}", payload);
        try {
            String[] parts = payload.split(",");
            if (parts.length == 3) {
                SensorData data = SensorData.builder()
                        .sensorId(parts[0])
                        .value(Double.parseDouble(parts[1]))
                        .unit(parts[2])
                        .timestamp(LocalDateTime.now())
                        .build();
                sensorDataRepository.save(data);
            } else {
                log.warn("Invalid message format: {}", payload);
            }
        } catch (Exception e) {
            log.error("Error processing MQTT message", e);
        }
    }
}
