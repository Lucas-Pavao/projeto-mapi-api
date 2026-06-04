package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.FloodEventDTO;
import com.projeto.mapi.dto.ScraperEventDTO;
import com.projeto.mapi.model.FloodEvent;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.FloodEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FloodEventServiceImpl implements FloodEventService {

    private final FloodEventRepository floodEventRepository;
    private final FloodPointRepository floodPointRepository;

    @Override
    @Transactional
    public FloodEventDTO reportFlood(FloodEventDTO dto) {
        FloodPoint point = floodPointRepository.findBySlug(dto.getFloodPointSlug())
                .orElseThrow(() -> new RuntimeException("Ponto de alagamento não encontrado: " + dto.getFloodPointSlug()));

        FloodEvent event = FloodEvent.builder()
                .floodPoint(point)
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .severity(dto.getSeverity())
                .description(dto.getDescription())
                .confirmedBy(dto.getConfirmedBy())
                .build();

        event = floodEventRepository.save(event);
        return convertToDTO(event);
    }

    @Override
    @Transactional
    public FloodEventDTO ingestScraperEvent(ScraperEventDTO dto) {
        List<FloodPoint> points = floodPointRepository.findAll();
        
        // Log para auditoria de distância
        points.forEach(p -> {
            double dist = calculateDistance(dto.getLatitude(), dto.getLongitude(), p.getLatitude(), p.getLongitude());
            if (dist < 10.0) { // Loga pontos próximos num raio de 10km para depuração
                log.debug("Distância até {}: {} km", p.getName(), String.format("%.2f", dist));
            }
        });

        FloodPoint nearestPoint = points.stream()
                .filter(p -> calculateDistance(dto.getLatitude(), dto.getLongitude(), p.getLatitude(), p.getLongitude()) < 3.0)
                .min(Comparator.comparingDouble(p -> calculateDistance(dto.getLatitude(), dto.getLongitude(), p.getLatitude(), p.getLongitude())))
                .orElseThrow(() -> new RuntimeException("Coordenadas [" + dto.getLatitude() + "," + dto.getLongitude() + 
                    "] fora do raio de 3km de qualquer ponto monitorado."));

        // Evitar duplicatas (mesmo ponto e mesma data, ignorando descrição exata para ser mais resiliente a mudanças de texto)
        boolean exists = floodEventRepository.findByFloodPointId(nearestPoint.getId()).stream()
                .anyMatch(e -> e.getStartTime().toLocalDate().equals(dto.getStartTime().toLocalDate()));
        
        if (exists) {
            log.debug("Evento já registrado para o ponto {} na data {}", nearestPoint.getName(), dto.getStartTime().toLocalDate());
            return null;
        }
        
        FloodEvent event = FloodEvent.builder()
                .floodPoint(nearestPoint)
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .severity(dto.getSeverity() != null ? dto.getSeverity() : FloodEvent.Severity.MEDIUM)
                .description(dto.getDescription())
                .confirmedBy(dto.getSource() != null ? dto.getSource() : "SCRAPER")
                .build();

        event = floodEventRepository.save(event);
        log.info("[✓] Novo evento de alagamento salvo para: {}", nearestPoint.getName());
        return convertToDTO(event);
    }

    @Override
    public List<FloodEventDTO> getHistoryByPoint(String slug) {
        FloodPoint point = floodPointRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Ponto de alagamento não encontrado: " + slug));

        return floodEventRepository.findByFloodPointId(point.getId()).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private FloodEventDTO convertToDTO(FloodEvent event) {
        return FloodEventDTO.builder()
                .id(event.getId())
                .floodPointSlug(event.getFloodPoint().getSlug())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .severity(event.getSeverity())
                .description(event.getDescription())
                .confirmedBy(event.getConfirmedBy())
                .build();
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // Raio da Terra em km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
