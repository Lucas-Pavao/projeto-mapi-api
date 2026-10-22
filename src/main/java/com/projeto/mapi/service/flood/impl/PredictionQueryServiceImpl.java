package com.projeto.mapi.service.flood.impl;

import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.dto.PredictionSeriesDTO;
import com.projeto.mapi.dto.PredictionSummaryDTO;
import com.projeto.mapi.dto.RiskLevel;
import com.projeto.mapi.dto.RiskSummaryDTO;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.exception.ResourceNotFoundException;
import com.projeto.mapi.mapper.PredictionSummaryMapper;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.flood.FloodPredictionHistoryService;
import com.projeto.mapi.service.flood.PredictionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PredictionQueryServiceImpl implements PredictionQueryService {

    static final Duration MAX_WINDOW = Duration.ofDays(7);

    private final FloodPointRepository pointRepository;
    private final FloodPredictionHistoryService history;
    private final PredictionSummaryMapper mapper;
    private final Clock clock;

    @Override
    public RiskSummaryDTO riskSummary() {
        int low = 0, medium = 0, high = 0, unknown = 0;
        for (FloodPoint point : pointRepository.findByActiveTrue()) {
            RiskLevel level = history.findLatest(point.getId(), point.getLatitude(), point.getLongitude())
                    .map(FloodPredictionResponseDTO::riskLevel).orElse(RiskLevel.UNKNOWN);
            switch (level) {
                case LOW -> low++;
                case MEDIUM -> medium++;
                case HIGH -> high++;
                case UNKNOWN -> unknown++;
            }
        }
        return new RiskSummaryDTO(low, medium, high, unknown, clock.instant());
    }

    @Override
    public List<PredictionSummaryDTO> latestPerActivePoint() {
        List<PredictionSummaryDTO> out = new ArrayList<>();
        for (FloodPoint point : pointRepository.findByActiveTrue()) {
            history.findLatest(point.getId(), point.getLatitude(), point.getLongitude())
                    .ifPresent(p -> out.add(mapper.toSummary(p, point.getSlug())));
        }
        return out;
    }

    @Override
    public PredictionSeriesDTO series(String slug, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new InvalidRequestException("from deve ser anterior a to");
        }
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new InvalidRequestException("Janela máxima de 7 dias");
        }
        FloodPoint point = pointRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Ponto não encontrado: " + slug));
        List<PredictionSummaryDTO> items = history.findSeries(point.getId(), from, to).stream()
                .map(p -> mapper.toSummary(p, slug)).toList();
        return new PredictionSeriesDTO(slug, from, to, items);
    }
}
