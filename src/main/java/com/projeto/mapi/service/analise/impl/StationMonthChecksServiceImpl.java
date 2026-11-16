package com.projeto.mapi.service.analise.impl;

import com.projeto.mapi.dto.StationMonthChecksDTO;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.repository.analise.PointRainRepository;
import com.projeto.mapi.repository.analise.PointRainRepository.MonthCheckRow;
import com.projeto.mapi.service.analise.PointRainService;
import com.projeto.mapi.service.analise.StationMonthChecksService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StationMonthChecksServiceImpl implements StationMonthChecksService {

    private final PointRainRepository repo;

    @Override
    public StationMonthChecksDTO grid(Instant from, Instant to) {
        Instant end = to != null ? to : Instant.now();
        ZonedDateTime first = (from != null ? from : end.atZone(ZoneOffset.UTC).minusMonths(5).toInstant())
                .atZone(ZoneOffset.UTC).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        if (!end.isAfter(first.toInstant())) throw new InvalidRequestException("'to' deve ser posterior a 'from'");
        List<Instant> months = new ArrayList<>();
        for (ZonedDateTime m = first; m.toInstant().isBefore(end); m = m.plusMonths(1)) months.add(m.toInstant());
        if (months.size() > MAX_MONTHS) throw new InvalidRequestException("Janela máxima de " + MAX_MONTHS + " meses");

        Map<String, StationMonthChecksDTO.StationRow> rows = new LinkedHashMap<>();
        Map<String, List<StationMonthChecksDTO.Cell>> cells = new LinkedHashMap<>();
        for (MonthCheckRow r : repo.monthChecks(PointRainService.SOURCE, PointRainService.RULE_VERSION, first.toInstant(), end)) {
            cells.computeIfAbsent(r.stationId(), k -> new ArrayList<>()).add(new StationMonthChecksDTO.Cell(r.monthStart(),
                    "PASS".equals(r.status()) ? "OK" : r.status(), r.reasons()));
            rows.putIfAbsent(r.stationId(), new StationMonthChecksDTO.StationRow(r.stationId(), r.name(), cells.get(r.stationId())));
        }
        return new StationMonthChecksDTO(PointRainService.RULE_VERSION, months, new ArrayList<>(rows.values()));
    }
}
