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
    private final com.projeto.mapi.service.alagamento.AiHealthClient health;
    private final boolean enabled;
    private final boolean shadow;
    private final java.time.Clock clock;
    private final com.projeto.mapi.config.ObrasProperties obras;

    public FloodForecastServiceImpl(FloodForecastClient client, RainForecastService rainForecast, FloodForecastRepository repo,
                                    ObjectMapper mapper, com.projeto.mapi.service.alagamento.AiHealthClient health,
                                    @Value("${mapi.alagamento.enabled:true}") boolean enabled,
                                    @Value("${mapi.alagamento.shadow:true}") boolean shadow, java.time.Clock clock,
                                    com.projeto.mapi.config.ObrasProperties obras) {
        this.obras = obras;
        this.client = client;
        this.rainForecast = rainForecast;
        this.repo = repo;
        this.mapper = mapper;
        this.health = health;
        this.enabled = enabled;
        this.shadow = shadow;
        this.clock = clock;
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
                    in.terrainStatus(), in.trYears(), in.teMinutes(),
                    in.factors() == null ? FloodForecastClient.Factors.NONE : in.factors());
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
            c.put("obra_periodo", obras.periodo(p.getSlug(), now));   // onda 16, Fase 3.4: validação nunca mistura antes e depois
            if (ai == null) {
                payload.put("level", "SEM_DADO");
                payload.put("reason", dto.reason());
            }
            JsonNode fx = payload.path("explanation").path("factors");
            JsonNode rf = fx.path("refluxo"), lz = fx.path("limpeza");
            repo.insert(new FloodForecastRepository.Row(cycleId, p.getId(), now, dto.issuedAt(), dto.level(),
                    dto.worstHorizonHours(), dto.reason(), dto.hNowCm(), dto.modelVersions().get("flood"),
                    dto.modelVersions().get("rain"), dto.modelVersions().get("bucket"), dto.codeVersion(),
                    mapper.writeValueAsString(payload), in.rainStation(), in.rainStationKm(), shadow,
                    in.factors() == null ? null : in.factors().refluxoMode(), in.factors() == null ? null : in.factors().limpezaMode(),
                    rf.path("served_delta_cm").isNumber() ? rf.path("served_delta_cm").asDouble() : null,
                    lz.path("p_clogged").isNumber() ? lz.path("p_clogged").asDouble() : null,
                    rf.path("bias_scenario").path("level").isTextual() ? rf.path("bias_scenario").path("level").asText() : null));
        } catch (Exception e) {
            log.error("Alerta por regressão do ponto {}: falha ao gravar: {}", p.getSlug(), e.toString());
        }
        return dto;
    }

    @Override
    public com.projeto.mapi.dto.AlagamentoHistoricoDTO history(int hours) {
        Instant to = clock.instant();
        Instant from = to.minus(java.time.Duration.ofHours(hours));
        java.util.Map<String, com.projeto.mapi.dto.AlagamentoHistoricoDTO.Series> by = new java.util.LinkedHashMap<>();
        for (FloodForecastRepository.HistoryRow r : repo.history(from, to)) {
            by.computeIfAbsent(r.slug(), k -> new com.projeto.mapi.dto.AlagamentoHistoricoDTO.Series(r.slug(), r.name(), new ArrayList<>()))
                    .items().add(new com.projeto.mapi.dto.AlagamentoHistoricoDTO.Item(r.at(), r.level(), r.worstHorizonHours(), r.hNowCm(), r.reason()));
        }
        return new com.projeto.mapi.dto.AlagamentoHistoricoDTO(from, to, new ArrayList<>(by.values()));
    }

    @Override
    public com.projeto.mapi.dto.AlagamentoStatusDTO status() {
        JsonNode h = health.health();
        var cycle = repo.lastCycle().orElse(null);
        java.util.function.Function<String, String> f = k -> h == null || h.path(k).isMissingNode() || h.path(k).isNull() ? null : h.path(k).asText();
        return new com.projeto.mapi.dto.AlagamentoStatusDTO(h != null, f.apply("rain_model_version"), f.apply("bucket_version"),
                f.apply("flood_version"), f.apply("code_version"), f.apply("code_dirty"), cycle == null ? null : cycle.at(),
                cycle == null ? java.util.Map.of() : cycle.byLevel(), cycle == null ? java.util.Map.of() : cycle.semDadoByReason(), shadow);
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
