package com.projeto.mapi.service.sensor.cemaden.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.dto.MeasuredRainAccumulationsDTO;
import com.projeto.mapi.dto.StationAccumulationDTO;
import com.projeto.mapi.exception.ServiceUnavailableException;
import com.projeto.mapi.repository.station.StationCanonicalRepository;
import com.projeto.mapi.repository.station.StationCanonicalRepository.CanonicalView;
import com.projeto.mapi.service.ingestion.adapter.ped.CemadenPedClient;
import com.projeto.mapi.service.sensor.cemaden.MeasuredRainAccumulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Acumulados medidos da PED ({@code /pcds-acum/acumulados-recentes}), casados com a estação canônica.
 *
 * <p>Regras da fonte que viram código aqui:
 * <ul>
 *   <li>12 requisições por minuto para usuário externo: a busca usa o mesmo {@link CemadenPedClient} da carga histórica,
 *       que faz uma requisição a cada 6 s, sem rajada. Uma volta pelos municípios da RMR (~14) leva ~1,5 min, por isso
 *       roda agendada, fora da requisição HTTP, e a rota serve o cache;</li>
 *   <li>UTC: {@code datahora} vem sem sufixo e é o instante do cálculo em UTC (conferido em 2026-11-15);</li>
 *   <li>HTTP 202 "Nenhum resultado foi encontrado" = município sem dado: as estações dele ficam {@code NO_DATA},
 *       nunca zero de chuva. Janela {@code null} = sem leitura na janela; continua nula.</li>
 * </ul>
 * Municípios consultados = os dos códigos das estações canônicas (os 6 primeiros dígitos do código do Cemaden são o
 * município do IBGE, mais o dígito verificador).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeasuredRainAccumulationServiceImpl implements MeasuredRainAccumulationService {

    public static final String ATTRIBUTION = "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC";
    static final String[] WINDOWS = {"acc1hr", "acc3hr", "acc6hr", "acc12hr", "acc24hr", "acc48hr", "acc72hr", "acc96hr", "acc120hr"};

    private final CemadenPedClient client;
    private final StationCanonicalRepository stations;
    private final IngestionProperties props;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final com.projeto.mapi.service.station.StationUnificationService unification;

    /** Por município: itens da última resposta 200, ou lista vazia se 202. Mantido entre ciclos quando uma consulta falha. */
    private final Map<String, List<JsonNode>> byMunicipality = new HashMap<>();
    private volatile MeasuredRainAccumulationsDTO snapshot;
    private final AtomicBoolean running = new AtomicBoolean();

    record Item(String code, Instant computedAt, Double[] acc) {}

    @Override
    public MeasuredRainAccumulationsDTO current() {
        MeasuredRainAccumulationsDTO s = snapshot;
        if (s == null) throw new ServiceUnavailableException("Acumulados medidos do Cemaden ainda não foram lidos");
        boolean stale = Duration.between(s.fetchedAtUtc(), clock.instant()).toMillis()
                > props.getCemadenPed().getAccumulationStaleAfterMs();
        return stale == s.stale() ? s : new MeasuredRainAccumulationsDTO(s.computedAtUtc(), s.fetchedAtUtc(), stale,
                s.timezone(), s.unit(), s.rainOrigin(), s.source(), s.attribution(), s.municipalitiesQueried(),
                s.municipalitiesWithoutData(), s.municipalitiesFailed(), s.stations());
    }

    /** Agendado; roda no {@code taskExecutor} (@Async) para não prender o agendador (~1,5 min por volta). */
    @org.springframework.scheduling.annotation.Async("taskExecutor")
    @Scheduled(fixedDelayString = "${mapi.ingestion.cemaden-ped.accumulation-refresh-ms:600000}",
            initialDelayString = "${mapi.ingestion.cemaden-ped.accumulation-initial-delay-ms:30000}")
    @Override
    public void scheduledRefresh() {
        IngestionProperties.CemadenPed cfg = props.getCemadenPed();
        if (!cfg.isAccumulationEnabled() || cfg.getEmail() == null || cfg.getEmail().isBlank()) return;
        if (!running.compareAndSet(false, true)) return;
        try {
            refresh();
        } catch (RuntimeException e) {
            log.warn("Acumulados medidos do Cemaden: ciclo falhou: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    @Override
    public synchronized void refresh() {
        List<CanonicalView> canonicals = stations.canonicals();
        if (canonicals.isEmpty()) {
            // Banco recém-migrado (V25): sem canônicas não há municípios a consultar. Unifica uma vez; é derivado.
            log.info("Sem estação canônica: rodando a unificação antes da primeira leitura dos acumulados");
            unification.unify();
            canonicals = stations.canonicals();
        }
        TreeSet<String> municipalities = new TreeSet<>();
        canonicals.stream().map(CanonicalView::ibgeCode).filter(Objects::nonNull).forEach(municipalities::add);
        List<String> withoutData = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String ibge : municipalities) {
            try {
                CemadenPedClient.Response r = client.recentAccumulations(ibge);
                if (r.noData()) {
                    byMunicipality.put(ibge, List.of());
                    withoutData.add(ibge);
                    continue;
                }
                JsonNode root = mapper.readTree(r.body());
                if (!root.isArray()) throw new IllegalStateException("resposta não é lista");
                List<JsonNode> items = new ArrayList<>();
                root.forEach(items::add);
                byMunicipality.put(ibge, items);
            } catch (Exception e) {
                // Falha de um município não derruba os outros; fica o valor do ciclo anterior, se houver.
                failed.add(ibge);
                log.warn("PED acumulados {}: {}", ibge, e.getMessage());
            }
        }
        if (failed.size() == municipalities.size() && !municipalities.isEmpty() && snapshot != null) return;
        Instant now = clock.instant();
        Instant since = now.minus(Duration.ofDays(5));
        snapshot = build(canonicals, stations.canonicalByStationId(StationUnificationSources.PED),
                stations.canonicalByStationId(StationUnificationSources.LIVE), stations.lastReadingByCanonical(since),
                List.copyOf(municipalities), withoutData, failed, now);
        long noData = snapshot.stations().stream().filter(st -> "NO_DATA".equals(st.status())).count();
        long withAcc3h = snapshot.stations().stream().filter(st -> st.acc3hMm() != null).count();
        long unknown = snapshot.stations().stream().filter(st -> st.canonicalId() == null).count();
        log.info("Acumulados medidos do Cemaden: {} estações ({} com acumulado de 3 h, {} NO_DATA, {} fora do cadastro), "
                        + "{} municípios sem dado, {} com falha", snapshot.stations().size(), withAcc3h, noData, unknown,
                withoutData.size(), failed.size());
    }

    MeasuredRainAccumulationsDTO build(List<CanonicalView> canonicals, Map<String, String> pedAlias,
                                       Map<String, String> liveAlias, Map<String, Instant> lastReading,
                                       List<String> queried, List<String> withoutData, List<String> failed, Instant now) {
        Map<String, Item> byCanonical = new LinkedHashMap<>();
        Map<String, Item> unknown = new LinkedHashMap<>();
        Instant computedAt = null;
        for (List<JsonNode> items : byMunicipality.values()) {
            for (JsonNode n : items) {
                Item it = parse(n);
                if (it == null) continue;
                if (it.computedAt() != null && (computedAt == null || it.computedAt().isAfter(computedAt))) computedAt = it.computedAt();
                String canonical = pedAlias.getOrDefault(it.code(), liveAlias.get(it.code()));
                if (canonical == null) unknown.put(it.code(), it);
                else byCanonical.putIfAbsent(canonical, it);
            }
        }
        List<StationAccumulationDTO> out = new ArrayList<>();
        for (CanonicalView c : canonicals) {
            Item it = byCanonical.get(c.canonicalId());
            out.add(dto(c.canonicalId(), it == null ? null : it.code(), c.name(), c.municipality(), c.lat(), c.lon(),
                    c.aliases(), lastReading.get(c.canonicalId()), it == null ? new Double[WINDOWS.length] : it.acc()));
        }
        // Estação que a PED devolve mas não está no cadastro do MAPi (ex.: rede de outro órgão): entra sem coordenada.
        for (Item it : unknown.values()) {
            out.add(dto(null, it.code(), null, null, null, null, List.of(), null, it.acc()));
        }
        return new MeasuredRainAccumulationsDTO(computedAt, now, false, "UTC", "mm", "MEASURED", "CEMADEN_PED",
                ATTRIBUTION, queried, List.copyOf(withoutData), List.copyOf(failed), out);
    }

    private static StationAccumulationDTO dto(String canonicalId, String code, String name, String municipality, Double lat,
                                              Double lon, List<String> aliases, Instant last, Double[] a) {
        boolean any = false;
        for (Double v : a) any |= v != null;
        return new StationAccumulationDTO(canonicalId, code, name, municipality, lat, lon, aliases, any ? "OK" : "NO_DATA",
                last, a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8]);
    }

    static Item parse(JsonNode n) {
        String code = n.path("codestacao").asText(null);
        if (code == null || code.isBlank()) return null;
        Double[] acc = new Double[WINDOWS.length];
        for (int i = 0; i < WINDOWS.length; i++) {
            JsonNode v = n.path(WINDOWS[i]);
            acc[i] = v.isNumber() ? v.asDouble() : null; // null ou ausente = sem leitura na janela, nunca 0
        }
        return new Item(code, parseUtc(n.path("datahora").asText("")), acc);
    }

    /** {@code 2026-11-15T17:27:20.828520}: sem sufixo, em UTC. */
    static Instant parseUtc(String text) {
        try {
            return LocalDateTime.parse(text.trim()).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Nomes das fontes, sem depender do pacote do serviço de unificação. */
    static final class StationUnificationSources {
        static final String PED = "CEMADEN_PED";
        static final String LIVE = "CEMADEN";

        private StationUnificationSources() {
        }
    }
}
