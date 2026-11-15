package com.projeto.mapi.service.analise.impl;

import com.projeto.mapi.dto.PointRainCandidatesDTO;
import com.projeto.mapi.dto.PointRainCandidatesDTO.Candidate;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.exception.ResourceNotFoundException;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.analise.PointRainRepository;
import com.projeto.mapi.repository.analise.PointRainRepository.HourRow;
import com.projeto.mapi.repository.analise.PointRainRepository.LinkedStation;
import com.projeto.mapi.repository.analise.PointRainRepository.MonthCheckRow;
import com.projeto.mapi.repository.analise.PointRainRepository.StoredHour;
import com.projeto.mapi.service.analise.PointRainCandidatesService;
import com.projeto.mapi.service.analise.PointRainService;
import com.projeto.mapi.service.sensor.cemaden.impl.MeasuredRainAccumulationServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class PointRainCandidatesServiceImpl implements PointRainCandidatesService {

    private static final double RADIUS_M = 5000;

    private final FloodPointRepository floodPoints;
    private final PointRainRepository repo;

    @Override
    public PointRainCandidatesDTO candidates(String slug, Instant at) {
        FloodPoint point = floodPoints.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Ponto não encontrado: " + slug));
        String rule = PointRainService.RULE_VERSION;
        Instant hourEnd = at != null ? at : repo.latestPointHour(point.getId(), rule).orElse(null);
        if (hourEnd == null) {
            return new PointRainCandidatesDTO(slug, null, RADIUS_M, rule, null, null, List.of(),
                    MeasuredRainAccumulationServiceImpl.ATTRIBUTION);
        }
        if (!hourEnd.equals(hourEnd.truncatedTo(ChronoUnit.HOURS))) {
            throw new InvalidRequestException("'at' deve ser uma hora cheia (fim da hora, UTC)");
        }
        // Mês civil UTC da hora: a hora que termina à meia-noite pertence ao mês anterior.
        ZonedDateTime inHour = hourEnd.minusNanos(1).atZone(ZoneOffset.UTC);
        Instant monthStart = inHour.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant nextMonth = inHour.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).plusMonths(1).toInstant();
        Map<String, MonthCheckRow> checks = new HashMap<>();
        for (MonthCheckRow r : repo.monthChecks(PointRainService.SOURCE, rule, monthStart, nextMonth)) checks.put(r.stationId(), r);

        StoredHour stored = repo.pointHours(point.getId(), rule, hourEnd, hourEnd).stream().findFirst().orElse(null);
        Double value = stored == null ? null : stored.value();
        String used = stored == null || stored.value() == null ? null : stored.stationId();

        List<Candidate> out = new ArrayList<>();
        for (LinkedStation s : repo.linkedStations(point.getId(), PointRainService.SOURCE, hourEnd)) {
            MonthCheckRow chk = checks.get(s.stationId());
            String status = chk == null ? null : "PASS".equals(chk.status()) ? "OK" : chk.status();
            List<String> reasons = chk == null ? List.of() : chk.reasons();
            Double mm = repo.hourly(PointRainService.SOURCE, s.stationId(), hourEnd.minus(1, ChronoUnit.HOURS), hourEnd)
                    .stream().filter(h -> h.hourEndUtc().equals(hourEnd)).map(HourRow::value)
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
            boolean valid = status == null || "OK".equals(status) || "UNCHECKED".equals(status);
            String role = !valid ? "EXCLUDED" : mm == null ? "NO_VALUE" : s.stationId().equals(used) ? "USED" : "LOWER";
            out.add(new Candidate(s.stationId(), s.name(), s.distanceM(), mm, status, reasons, role));
        }
        return new PointRainCandidatesDTO(slug, hourEnd, RADIUS_M, rule, value, used, out,
                MeasuredRainAccumulationServiceImpl.ATTRIBUTION);
    }
}
