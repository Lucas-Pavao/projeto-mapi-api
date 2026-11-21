package com.projeto.mapi.service.alagamento.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.dto.AlagamentoPontoDTO;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.alagamento.FloodForecastRepository;
import com.projeto.mapi.service.alagamento.FloodForecastClient;
import com.projeto.mapi.service.alagamento.FloodForecastMapper;
import com.projeto.mapi.service.alagamento.FloodForecastService;
import com.projeto.mapi.service.flood.RainForecastService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FloodForecastServiceImpl implements FloodForecastService {

    /** Campos que a API acrescenta ao payload da IA antes de gravar (a IA não conhece a estação nem a série do ponto). */
    static final String CTX = "api_context";

    private final FloodForecastClient client;
    private final RainForecastService rainForecast;
    private final FloodForecastRepository repo;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final boolean shadow;

    public FloodForecastServiceImpl(FloodForecastClient client, RainForecastService rainForecast, FloodForecastRepository repo,
                                    ObjectMapper mapper, @Value("${mapi.alagamento.enabled:true}") boolean enabled,
                                    @Value("${mapi.alagamento.shadow:true}") boolean shadow) {
        this.client = client;
        this.rainForecast = rainForecast;
        this.repo = repo;
        this.mapper = mapper;
        this.enabled = enabled;
        this.shadow = shadow;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean shadow() {
        return shadow;
    }

    @Override
    public AlagamentoPontoDTO evaluate(UUID cycleId, Instant now, FloodPoint p, Input in) {
        String stationName = null;
        try {
            stationName = repo.stationName(in.rainStation());
        } catch (Exception e) {
            log.warn("Nome da estação {} indisponível: {}", in.rainStation(), e.toString());
        }
        FloodForecastMapper.PointContext ctx = new FloodForecastMapper.PointContext(p.getId(), p.getSlug(), p.getName(),
                in.rainStation(), stationName, in.rainStationKm(), in.rainLastHourMm(), now, shadow);
        JsonNode ai = null;
        try {
            RainForecastService.Inputs ri = rainForecast.inputs(p.getId(), p.getSlug(), p.getLatitude(), p.getLongitude());
            ai = client.forecast(p.getLatitude(), p.getLongitude(), ri.readings(), ri.openMeteo(), in.rain10min(), in.terrain(),
                    in.terrainStatus(), in.trYears(), in.teMinutes());
        } catch (Exception e) {
            log.warn("Alerta por regressão do ponto {}: entradas indisponíveis: {}", p.getSlug(), e.toString());
        }
        AlagamentoPontoDTO dto = FloodForecastMapper.toDto(ai, ctx);
        try {
            ObjectNode payload = ai != null && ai.isObject() ? ((ObjectNode) ai).deepCopy() : mapper.createObjectNode();
            ObjectNode c = payload.putObject(CTX);
            c.put("rain_last_hour_mm", in.rainLastHourMm());
            c.put("rain_station", in.rainStation());
            c.put("rain_station_km", in.rainStationKm());
            c.put("series_steps", in.rain10min() == null ? 0 : in.rain10min().size());
            if (ai == null) {
                payload.put("level", "SEM_DADO");
                payload.put("reason", dto.reason());
            }
            repo.insert(new FloodForecastRepository.Row(cycleId, p.getId(), now, dto.issuedAt(), dto.level(),
                    dto.worstHorizonHours(), dto.reason(), dto.hNowCm(), dto.modelVersions().get("flood"),
                    dto.modelVersions().get("rain"), dto.modelVersions().get("bucket"), dto.codeVersion(),
                    mapper.writeValueAsString(payload), in.rainStation(), in.rainStationKm(), shadow));
        } catch (Exception e) {
            log.error("Alerta por regressão do ponto {}: falha ao gravar: {}", p.getSlug(), e.toString());
        }
        return dto;
    }

    @Override
    public List<AlagamentoPontoDTO> current() {
        List<AlagamentoPontoDTO> out = new ArrayList<>();
        for (FloodForecastRepository.Latest l : repo.latestPerActivePoint()) {
            JsonNode ai;
            try {
                ai = mapper.readTree(l.payloadJson());
            } catch (Exception e) {
                ai = null;
            }
            Double rainHour = ai == null ? null : ai.path(CTX).path("rain_last_hour_mm").isNumber()
                    ? ai.path(CTX).path("rain_last_hour_mm").asDouble() : null;
            FloodForecastMapper.PointContext ctx = new FloodForecastMapper.PointContext(l.floodPointId(), l.slug(), l.name(),
                    l.rainStation(), l.rainStationName(), l.rainStationKm(), rainHour, l.evaluatedAt(), l.shadow());
            out.add(FloodForecastMapper.toDto(ai, ctx));
        }
        return out;
    }
}
