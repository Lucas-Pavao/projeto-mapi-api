package com.projeto.mapi.service.flood.impl;

import com.projeto.mapi.dto.FloodEventDTO;
import com.projeto.mapi.dto.ScraperEventDTO;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.model.FloodEvent;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.flood.FloodEventService;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

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
                .orElseThrow(() -> new com.projeto.mapi.exception.ResourceNotFoundException("Ponto de alagamento não encontrado: " + dto.getFloodPointSlug()));

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
            double dist = GeoUtils.calculateDistance(dto.getLatitude(), dto.getLongitude(), p.getLatitude(), p.getLongitude());
            if (dist < 10.0) { // Loga pontos próximos num raio de 10km para depuração
                log.debug("Distância até {}: {} km", p.getName(), String.format("%.2f", dist));
            }
        });

        FloodPoint nearestPoint = points.stream()
                .filter(p -> GeoUtils.calculateDistance(dto.getLatitude(), dto.getLongitude(), p.getLatitude(), p.getLongitude()) < 3.0)
                .min(Comparator.comparingDouble(p -> GeoUtils.calculateDistance(dto.getLatitude(), dto.getLongitude(), p.getLatitude(), p.getLongitude())))
                .orElseThrow(() -> new InvalidRequestException("Coordenadas [" + dto.getLatitude() + "," + dto.getLongitude() +
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
    public Page<FloodEventDTO> getHistoryByPoint(String slug, Pageable pageable) {
        FloodPoint point = floodPointRepository.findBySlug(slug)
                .orElseThrow(() -> new com.projeto.mapi.exception.ResourceNotFoundException("Ponto de alagamento não encontrado: " + slug));

        return floodEventRepository.findByFloodPointIdOrderByStartTimeDesc(point.getId(), pageable)
                .map(this::convertToDTO);
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
}
