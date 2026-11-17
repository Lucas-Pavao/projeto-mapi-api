package com.projeto.mapi.service.radar.impl;

import com.projeto.mapi.config.RadarProperties;
import com.projeto.mapi.exception.SourceUnavailableException;
import com.projeto.mapi.repository.radar.RadarImageRepository;
import com.projeto.mapi.repository.radar.RadarImageRepository.Entry;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.radar.ApacRadarClient;
import com.projeto.mapi.service.radar.RadarArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Coletor do radar da APAC (operação O1). A cada 10 min lê a listagem do diretório, baixa o que ainda não está gravado
 * e registra como ABSENT o instante esperado (passo de 10 min) que não aparece na listagem. Arquivo já presente no
 * volume (backfill manual de 2026-11-17) é só conferido e registrado, sem novo download. Nada é apagado.
 * O nome do arquivo está em UTC (verificado contra o serviço do painel da APAC, V33).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RadarArchiveServiceImpl implements RadarArchiveService {

    static final DateTimeFormatter NAME_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final ApacRadarClient client;
    private final RadarImageRepository repo;
    private final RadarProperties props;
    private final Clock clock;

    @Scheduled(fixedRateString = "${mapi.radar.fixed-rate-ms:600000}", initialDelayString = "${mapi.radar.initial-delay-ms:30000}")
    public void scheduled() {
        if (!props.isEnabled()) return;
        try {
            Result r = collect();
            log.info("Radar APAC: {} gravada(s), {} ausente(s), {} falha(s)", r.saved(), r.absent(), r.errors());
        } catch (Exception e) {
            log.warn("Radar APAC: rodada falhou: {}", e.toString());
        }
    }

    static Instant tsOf(String fileName) {
        return LocalDateTime.parse(fileName.substring(0, 12), NAME_TS).toInstant(ZoneOffset.UTC);
    }

    static String productOf(String fileName) {
        String rest = fileName.substring(13);
        return rest.substring(0, rest.indexOf("_trans.png"));
    }

    static String relPath(String fileName) {
        return fileName.substring(0, 4) + "/" + fileName.substring(4, 6) + "/" + fileName.substring(6, 8) + "/" + fileName;
    }

    @Override
    public Result collect() {
        Instant now = clock.instant();
        Instant from = now.minus(Duration.ofHours(props.getLookbackHours()));
        List<String> listing;
        try {
            listing = client.listFiles();
        } catch (SourceUnavailableException e) {
            log.warn("Radar APAC: listagem indisponível: {}", e.getMessage());
            return new Result(0, 0, 1, true);
        }
        int saved = 0, absent = 0, errors = 0;
        Path root = Path.of(props.getDirectory());
        for (String product : props.getProducts()) {
            Set<Instant> stored = repo.stored(product, from);
            Map<Instant, String> listed = new HashMap<>();
            for (String f : listing) {
                if (!productOf(f).equals(product)) continue;
                Instant ts = tsOf(f);
                if (!ts.isBefore(from)) listed.put(ts, f);
            }
            for (Map.Entry<Instant, String> e : new java.util.TreeMap<>(listed).entrySet()) {
                if (stored.contains(e.getKey())) continue;
                try {
                    if (saveOne(root, product, e.getKey(), e.getValue(), now)) saved++; else absent++;
                    sleep();
                } catch (Exception ex) {
                    errors++;
                    repo.upsert(new Entry(product, e.getKey(), "ERROR", e.getValue(), null, null, null, null, now, ex.toString()));
                }
            }
            absent += markMissing(product, listed, from, now);
        }
        return new Result(saved, absent, errors, false);
    }

    private boolean saveOne(Path root, String product, Instant ts, String fileName, Instant now) throws IOException {
        String rel = relPath(fileName);
        Path target = root.resolve(rel);
        byte[] bytes;
        String note = null;
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            bytes = Files.readAllBytes(target);
            note = "arquivo já presente no volume (backfill)";
        } else {
            Optional<byte[]> got = client.fetch(fileName);
            if (got.isEmpty()) {
                repo.upsert(new Entry(product, ts, "ABSENT", fileName, null, null, null, 404, now, "404 na listagem"));
                return false;
            }
            bytes = got.get();
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(fileName + ".part");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        repo.upsert(new Entry(product, ts, "OK", fileName, rel, Hashing.sha256(bytes), bytes.length, note == null ? 200 : null,
                now, note));
        return true;
    }

    /** Instantes esperados (passo de 10 min, alinhados aos da listagem) sem arquivo na listagem e já velhos: ABSENT. */
    private int markMissing(String product, Map<Instant, String> listed, Instant from, Instant now) {
        if (listed.isEmpty()) return 0;
        TreeSet<Instant> ts = new TreeSet<>(listed.keySet());
        Instant first = ts.first();
        Instant limit = now.minus(Duration.ofMinutes(props.getAbsentAfterMinutes()));
        Set<Instant> known = repo.known(product, from);
        int n = 0;
        for (Instant t = first; !t.isAfter(limit); t = t.plus(props.getStepMinutes(), ChronoUnit.MINUTES)) {
            if (listed.containsKey(t) || known.contains(t)) continue;
            String name = NAME_TS.format(t.atOffset(ZoneOffset.UTC)) + "." + product + "_trans.png";
            repo.upsert(new Entry(product, t, "ABSENT", name, null, null, null, null, now, "fora da listagem do servidor"));
            n++;
        }
        return n;
    }

    private void sleep() {
        try {
            Thread.sleep(props.getMinIntervalMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
