package com.projeto.mapi.service;

import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataCollectionTask {

    private final FloodPointRepository floodPointRepository;
    private final WeatherService weatherService;

    // Executa a cada 1 hora (3600000 ms)
    @Scheduled(fixedRate = 3600000)
    public void collectWeatherDataForAllPoints() {
        log.info("Iniciando coleta agendada de dados meteorológicos para todos os pontos...");
        List<FloodPoint> points = floodPointRepository.findAll();
        
        for (FloodPoint point : points) {
            try {
                weatherService.getWeatherData(point.getLatitude(), point.getLongitude());
            } catch (Exception e) {
                log.error("Erro ao coletar clima para o ponto {}: {}", point.getName(), e.getMessage());
            }
        }
        log.info("Coleta agendada concluída para {} pontos.", points.size());
    }
}
