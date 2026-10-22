package com.projeto.mapi.service.sensor.impl;

import com.projeto.mapi.repository.ingestion.ObservationReadRepository;
import com.projeto.mapi.service.sensor.HourlyRainService;
import com.projeto.mapi.service.sensor.RainSpatialCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HourlyRainServiceImpl implements HourlyRainService {

    private final ObservationReadRepository observations;
    private final Clock clock;

    @Override
    public List<StationRain> latestClosedHour() {
        Instant now = clock.instant();
        return RainSpatialCheck.dropIsolatedPeaks(observations.latestClosedHourRain(now, now.minus(MAX_AGE_HOURS, ChronoUnit.HOURS))).stream()
                .map(r -> new StationRain(r.source(), r.stationId(), r.hourEndUtc(), r.mm(), r.lat(), r.lon()))
                .toList();
    }
}
