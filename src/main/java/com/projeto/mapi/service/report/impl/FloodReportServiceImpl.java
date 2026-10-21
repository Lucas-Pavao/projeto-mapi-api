package com.projeto.mapi.service.report.impl;

import com.projeto.mapi.dto.FloodReportDTO;
import com.projeto.mapi.dto.FloodReportInputDTO;
import com.projeto.mapi.dto.FloodReportReviewDTO;
import com.projeto.mapi.dto.PageResponseDTO;
import com.projeto.mapi.exception.ConflictException;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.exception.ResourceNotFoundException;
import com.projeto.mapi.mapper.FloodReportMapper;
import com.projeto.mapi.model.FloodEvent;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.FloodReport;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.FloodReportRepository;
import com.projeto.mapi.service.report.FloodReportService;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Slf4j
public class FloodReportServiceImpl implements FloodReportService {

    static final double NEAREST_POINT_RADIUS_KM = 3.0;
    static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    private final FloodReportRepository reportRepository;
    private final FloodPointRepository pointRepository;
    private final FloodEventRepository eventRepository;
    private final FloodReportMapper mapper;
    private final Clock clock;

    @Override
    @Transactional
    public FloodReportDTO submit(FloodReportInputDTO in, String username) {
        Instant now = clock.instant();
        if (in.observedAt().isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new InvalidRequestException("observedAt não pode estar no futuro");
        }
        FloodPoint nearest = pointRepository.findByActiveTrue().stream()
                .min(Comparator.comparingDouble(p -> dist(in, p)))
                .filter(p -> dist(in, p) <= NEAREST_POINT_RADIUS_KM)
                .orElse(null);

        FloodReport report = FloodReport.builder()
                .latitude(in.latitude())
                .longitude(in.longitude())
                .observedAt(in.observedAt())
                .waterDepth(in.waterDepth() == null ? null : FloodReport.WaterDepth.valueOf(in.waterDepth()))
                .description(in.description())
                .status(FloodReport.Status.PENDING)
                .reportedBy(username)
                .nearestPoint(nearest)
                .nearestPointDistanceM(nearest == null ? null : (int) Math.round(dist(in, nearest) * 1000.0))
                .createdAt(now)
                .build();
        return mapper.toDto(reportRepository.save(report));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<FloodReportDTO> list(String status, int page, int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var result = status == null
                ? reportRepository.findAll(pr)
                : reportRepository.findByStatus(parseStatus(status), pr);
        return new PageResponseDTO<>(result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getContent().stream().map(mapper::toDto).toList());
    }

    @Override
    @Transactional
    public FloodReportDTO review(Long id, FloodReportReviewDTO review, String username) {
        FloodReport report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Relato não encontrado: " + id));
        if (report.getStatus() != FloodReport.Status.PENDING) {
            throw new ConflictException("Relato já revisado: " + report.getStatus());
        }
        Instant now = clock.instant();
        if ("CONFIRMED".equals(review.status())) {
            if (isBlank(review.floodPointSlug()) || isBlank(review.severity())) {
                throw new InvalidRequestException("CONFIRMED exige floodPointSlug e severity");
            }
            FloodPoint point = pointRepository.findBySlug(review.floodPointSlug())
                    .orElseThrow(() -> new ResourceNotFoundException("Ponto não encontrado: " + review.floodPointSlug()));
            FloodEvent event = eventRepository.save(FloodEvent.builder()
                    .floodPoint(point)
                    // FloodEvent ainda guarda hora local sem fuso (ADR 0001 pendente): converte com o fuso da JVM,
                    // o mesmo usado em flood_predictions.timestamp.
                    .startTime(LocalDateTime.ofInstant(report.getObservedAt(), ZoneId.systemDefault()))
                    .severity(FloodEvent.Severity.valueOf(review.severity()))
                    .description(eventDescription(report, review.note()))
                    .confirmedBy(username)
                    .build());
            report.setStatus(FloodReport.Status.CONFIRMED);
            report.setFloodEventId(event.getId());
        } else {
            if (isBlank(review.reason())) {
                throw new InvalidRequestException("DISCARDED exige reason");
            }
            report.setStatus(FloodReport.Status.DISCARDED);
            report.setDiscardReason(review.reason());
        }
        report.setReviewedBy(username);
        report.setReviewedAt(now);
        log.info("Relato {} {} por {}", id, report.getStatus(), username);
        return mapper.toDto(reportRepository.save(report));
    }

    private static String eventDescription(FloodReport report, String note) {
        StringBuilder sb = new StringBuilder("Relato #").append(report.getId());
        if (report.getWaterDepth() != null) sb.append(" (lâmina: ").append(report.getWaterDepth()).append(")");
        if (!isBlank(report.getDescription())) sb.append(": ").append(report.getDescription());
        if (!isBlank(note)) sb.append(" | Nota do operador: ").append(note);
        return sb.toString();
    }

    private static FloodReport.Status parseStatus(String s) {
        try {
            return FloodReport.Status.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("status deve ser PENDING, CONFIRMED ou DISCARDED");
        }
    }

    private static double dist(FloodReportInputDTO in, FloodPoint p) {
        return GeoUtils.calculateDistance(in.latitude(), in.longitude(), p.getLatitude(), p.getLongitude());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
