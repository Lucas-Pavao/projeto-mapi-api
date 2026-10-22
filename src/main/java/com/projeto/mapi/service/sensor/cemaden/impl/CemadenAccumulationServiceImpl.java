package com.projeto.mapi.service.sensor.cemaden.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.dto.CemadenAccumulationDTO;
import com.projeto.mapi.exception.ServiceUnavailableException;
import com.projeto.mapi.exception.SourceUnavailableException;
import com.projeto.mapi.repository.ingestion.ObservationReadRepository;
import com.projeto.mapi.repository.ingestion.ObservationReadRepository.StationRainSum;
import com.projeto.mapi.service.ingestion.adapter.CemadenAdapter;
import com.projeto.mapi.service.sensor.cemaden.CemadenAccumulationClient;
import com.projeto.mapi.service.sensor.cemaden.CemadenAccumulationService;
import com.projeto.mapi.util.RmrFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê o painel público da CEMADEN, fica só com a RMR e casa cada estação com a gravada pelo MAPi (pelo município e
 * pelo nome sem as etiquetas "[CEMADEN]", "[APAC]", "[G]"...), para trazer código, coordenadas e a soma de 24 h dos
 * blocos que coletamos. O painel não tem coordenada nem código GMMC.
 *
 * <p>No painel, "-" num acumulado significa que não houve leitura com chuva na janela. Conta como 0 se a estação
 * transmitiu dentro da janela; senão fica nulo (estação calada não é "não choveu"). O horário da última leitura vem
 * em UTC ({@code dd/MM/yy HH:mm}): conferido em 2026-10-21 contra o feed da APAC (21:20 local = 00:20 do painel).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CemadenAccumulationServiceImpl implements CemadenAccumulationService {

    static final DateTimeFormatter PANEL_TS = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");
    private static final String[] WINDOWS = {"acc1hr", "acc3hr", "acc6hr", "acc12hr", "acc24hr", "acc48hr", "acc72hr", "acc96hr"};
    private static final int[] WINDOW_HOURS = {1, 3, 6, 12, 24, 48, 72, 96};

    private final CemadenAccumulationClient client;
    private final ObservationReadRepository observations;
    private final AppProperties appProperties;
    private final Clock clock;

    private volatile Snapshot cache;

    private record Snapshot(Instant fetchedAt, List<JsonNode> items) {}

    @Override
    public List<CemadenAccumulationDTO> rmr() {
        Instant now = clock.instant();
        Map<String, StationRainSum> ours = new HashMap<>();
        // As estações "[APAC]" do feed vêm sem município ("Não informada"): para elas vale o nome, se for único.
        Map<String, StationRainSum> byName = new HashMap<>();
        java.util.Set<String> repeated = new java.util.HashSet<>();
        for (StationRainSum s : observations.rainSumByStation(CemadenAdapter.SOURCE, now.minus(Duration.ofHours(24)), now)) {
            ours.putIfAbsent(key(s.municipality(), s.name()), s);
            String muni = clean(s.municipality());
            if (!muni.isEmpty() && !muni.equals("NAO INFORMADA")) continue;
            if (byName.putIfAbsent(nameKey(s.name()), s) != null) repeated.add(nameKey(s.name()));
        }
        repeated.forEach(byName::remove);
        List<CemadenAccumulationDTO> out = new ArrayList<>();
        for (JsonNode item : items(now)) {
            String city = item.path("cidade").asText("");
            String name = item.path("nomeestacao").asText("");
            if (!RmrFilter.isRmr(city, name)) continue;
            Instant last = parseTs(item.path("datahoraUltimovalor").asText(""));
            Double[] acc = new Double[WINDOWS.length];
            for (int i = 0; i < WINDOWS.length; i++) acc[i] = accumulation(item.path(WINDOWS[i]), last, now, WINDOW_HOURS[i]);
            StationRainSum s = ours.getOrDefault(key(city, name), byName.get(nameKey(name)));
            Double mapi24 = s == null ? null : s.mm();
            out.add(new CemadenAccumulationDTO(name, city, s == null ? null : s.stationId(),
                    s == null ? null : s.lat(), s == null ? null : s.lon(), item.path("tipoestacao").asInt(),
                    number(item.path("ultimovalor")), last, acc[0], acc[1], acc[2], acc[3], acc[4], acc[5], acc[6], acc[7],
                    mapi24, mapi24 == null || acc[4] == null ? null : round(mapi24 - acc[4])));
        }
        return out;
    }

    private List<JsonNode> items(Instant now) {
        Snapshot c = cache;
        if (c != null && Duration.between(c.fetchedAt(), now).toMillis() < appProperties.getCemaden().getCacheTtlMs()) {
            return c.items();
        }
        List<JsonNode> items;
        try {
            items = client.fetchState(appProperties.getCemaden().getUf());
        } catch (SourceUnavailableException e) {
            log.warn("Painel da CEMADEN indisponível: {}", e.getMessage());
            throw new ServiceUnavailableException("Painel da CEMADEN indisponível agora");
        }
        cache = new Snapshot(now, items);
        return items;
    }

    static Double accumulation(JsonNode v, Instant last, Instant now, int hours) {
        Double n = number(v);
        if (n != null) return n;
        return last != null && !last.isBefore(now.minus(Duration.ofHours(hours))) ? 0.0 : null;
    }

    private static Double number(JsonNode v) {
        if (v.isNumber()) return v.asDouble();
        if (v.isTextual()) {
            try {
                return Double.parseDouble(v.asText().trim().replace(',', '.'));
            } catch (NumberFormatException ignored) {
                return null; // "-" ou texto: sem valor
            }
        }
        return null;
    }

    static Instant parseTs(String text) {
        try {
            return LocalDateTime.parse(text.trim(), PANEL_TS).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Município + nome sem etiquetas entre colchetes, sem acento e só com letras e dígitos. */
    static String key(String municipality, String name) {
        return clean(municipality) + "|" + nameKey(name);
    }

    static String nameKey(String name) {
        return clean(name == null ? "" : name.replaceAll("\\[[^\\]]*\\]", " "));
    }

    private static String clean(String s) {
        return RmrFilter.normalize(s).replaceAll("[^A-Z0-9]+", " ").trim();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
