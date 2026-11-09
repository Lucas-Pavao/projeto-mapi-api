package com.projeto.mapi.service.analise.impl;

import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.dto.PointRainRebuildDTO;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.analise.PointRainRepository;
import com.projeto.mapi.repository.analise.PointRainRepository.CheckRow;
import com.projeto.mapi.repository.analise.PointRainRepository.LinkRow;
import com.projeto.mapi.repository.analise.PointRainRepository.PointHourRow;
import com.projeto.mapi.service.analise.PointRainComposer;
import com.projeto.mapi.service.analise.PointRainService;
import com.projeto.mapi.service.analise.StationMonthChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Monta, a partir da grade horária do Cemaden PED (V22/V24), a checagem espacial por estação e mês, a série de chuva
 * de cada ponto (maior valor entre as válidas dentro do raio) e o vínculo estação↔ponto com o período de operação
 * medido nos dados. Derivado e recalculável: apaga e regrava a faixa pedida numa transação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PointRainServiceImpl implements PointRainService {

    private static final Duration HOUR = Duration.ofHours(1);

    private final PointRainRepository repo;
    private final FloodPointRepository floodPoints;
    private final IngestionProperties props;

    /** Série de uma estação na janela: valor, leituras e minutos cobertos por hora (NaN = sem valor). */
    private record Series(PointRainRepository.StationRow st, double[] mm, int[] n, double[] cov, int first, int last) {}

    @Override
    @Transactional
    public PointRainRebuildDTO rebuild(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to) || !from.truncatedTo(ChronoUnit.HOURS).equals(from)
                || !to.truncatedTo(ChronoUnit.HOURS).equals(to)) {
            throw new InvalidRequestException("from < to, ambos em hora cheia UTC");
        }
        double radius = props.getSensorLinkageRadiusM();
        long linkageId = repo.linkageId(LINKAGE_METHOD, radius).orElseThrow(() -> new InvalidRequestException(
                "linkage_config SENSOR_POINT " + LINKAGE_METHOD + " com raio " + radius + " não existe (V24)"));
        StationMonthChecker.Rules rules = StationMonthChecker.Rules.defaults();
        int hours = (int) Duration.between(from, to).toHours();

        // 1. séries por estação, em vetor indexado pela hora (i = fim em from + (i+1) h)
        List<Series> series = new ArrayList<>();
        for (PointRainRepository.StationRow st : repo.stations(SOURCE)) {
            double[] mm = new double[hours], cov = new double[hours];
            int[] n = new int[hours];
            Arrays.fill(mm, Double.NaN);
            Arrays.fill(cov, Double.NaN);
            int first = -1, last = -1;
            for (PointRainRepository.HourRow h : repo.hourly(SOURCE, st.stationId(), from, to)) {
                int i = (int) Duration.between(from, h.hourEndUtc()).toHours() - 1;
                if (i < 0 || i >= hours || h.value() == null) continue;
                mm[i] = h.value();
                n[i] = h.nReadings();
                cov[i] = h.coveredMin() == null ? Double.NaN : h.coveredMin();
                if (first < 0) first = i;
                last = i;
            }
            series.add(new Series(st, mm, n, cov, first, last));
        }

        // 2. checagem por mês UTC
        List<Instant> monthStarts = new ArrayList<>();
        for (YearMonth m = YearMonth.from(from.atZone(ZoneOffset.UTC)); m.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                .isBefore(to); m = m.plusMonths(1)) {
            monthStarts.add(m.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        }
        Map<String, boolean[]> validByStation = new HashMap<>(); // por mês
        List<CheckRow> checks = new ArrayList<>();
        Map<String, Integer> byStatus = new TreeMap<>(), reasons = new TreeMap<>();
        for (int k = 0; k < monthStarts.size(); k++) {
            Instant ms = monthStarts.get(k);
            Instant me = YearMonth.from(ms.atZone(ZoneOffset.UTC)).plusMonths(1).atDay(1).atStartOfDay()
                    .toInstant(ZoneOffset.UTC);
            int a = Math.max(0, (int) Duration.between(from, ms).toHours()); // índice da 1a hora do mês
            int b = Math.min(hours, (int) Duration.between(from, me).toHours());
            if (b <= a) continue;
            List<StationMonthChecker.StationMonth> sm = new ArrayList<>();
            for (Series s : series) {
                sm.add(new StationMonthChecker.StationMonth(s.st().stationId(), s.st().lat(), s.st().lon(),
                        Arrays.copyOfRange(s.mm(), a, b), Arrays.copyOfRange(s.cov(), a, b)));
            }
            for (StationMonthChecker.Result r : StationMonthChecker.check(sm, rules)) {
                validByStation.computeIfAbsent(r.stationId(), x -> new boolean[monthStarts.size()])[k] = r.usable();
                byStatus.merge(r.status(), 1, Integer::sum);
                if ("FAIL".equals(r.status())) r.reasons().forEach(x -> reasons.merge(x, 1, Integer::sum));
                Map<String, Object> metrics = new LinkedHashMap<>();
                metrics.put("hourCoverage", r.hourCoverage());
                metrics.put("blockCoverage", r.blockCoverage());
                metrics.put("neighborsUsed", r.neighborsUsed());
                metrics.put("rules", rules.asMap());
                checks.add(new CheckRow(SOURCE, r.stationId(), ms, r.status(), r.reasons(), r.hoursInMonth(),
                        r.hoursWithValue(), r.hourCoverage(), r.totalMm(), r.nNeighbors(), r.ratioMedian(),
                        r.commonMmMedian(), metrics, RULE_VERSION));
            }
        }
        repo.replaceChecks(SOURCE, RULE_VERSION, monthStarts.get(0), to, checks);

        // 3. série por ponto e vínculo
        List<PointHourRow> rows = new ArrayList<>();
        List<LinkRow> links = new ArrayList<>();
        Map<String, Double> coverage = new LinkedHashMap<>();
        List<FloodPoint> pts = floodPoints.findByActiveTrue().stream().sorted(Comparator.comparing(FloodPoint::getSlug))
                .toList();
        for (FloodPoint p : pts) {
            List<Series> cands = new ArrayList<>();
            List<Double> dists = new ArrayList<>();
            for (Series s : series) {
                double d = StationMonthChecker.distanceM(p.getLatitude(), p.getLongitude(), s.st().lat(), s.st().lon());
                if (d > radius) continue;
                cands.add(s);
                dists.add(d);
                if (s.first() >= 0) {
                    links.add(new LinkRow(p.getId(), SOURCE, s.st().stationId(), round(d, 1), radius, linkageId,
                            from.plus(HOUR.multipliedBy(s.first())), from.plus(HOUR.multipliedBy(s.last() + 1L))));
                }
            }
            int covered = 0;
            for (int i = 0; i < hours; i++) {
                Instant hourEnd = from.plus(HOUR.multipliedBy(i + 1L));
                int k = monthIndex(monthStarts, hourEnd.minusNanos(1));
                List<PointRainComposer.Candidate> cs = new ArrayList<>();
                for (int j = 0; j < cands.size(); j++) {
                    Series s = cands.get(j);
                    boolean[] v = validByStation.get(s.st().stationId());
                    cs.add(new PointRainComposer.Candidate(s.st().stationId(), dists.get(j), v != null && v[k],
                            s.mm()[i], s.n()[i], s.cov()[i]));
                }
                PointRainComposer.Hour h = PointRainComposer.compose(cs);
                if (h.value() != null) covered++;
                rows.add(new PointHourRow(p.getId(), hourEnd, h.value(), SOURCE, h.stationId(),
                        h.distanceM() == null ? null : round(h.distanceM(), 1), h.nCandidates(), h.nValid(),
                        h.nValidWithValue(), h.value() == null ? null : h.nBlocks(), h.coveredMin(), linkageId,
                        RULE_VERSION));
            }
            coverage.put(p.getSlug(), round((double) covered / hours, 4));
        }
        repo.replacePointHours(RULE_VERSION, from, to, rows);
        repo.replaceLinks(linkageId, links);
        log.info("chuva-ponto: {} estações, {} meses, {} checagens {}, {} horas-ponto", series.size(),
                monthStarts.size(), checks.size(), byStatus, rows.size());
        return new PointRainRebuildDTO(from, to, series.size(), monthStarts.size(), checks.size(), byStatus, reasons,
                rows.size(), coverage, linkageId, rules.asMap(), List.of(
                "Mês = mês civil UTC; a checagem compara a estação com as vizinhas nas horas em que as duas têm valor.",
                "Valor da hora do ponto = maior valor entre as estações aprovadas (PASS ou UNCHECKED) no mês, dentro "
                        + "do raio; sem nenhuma com valor, lacuna."));
    }

    private static int monthIndex(List<Instant> starts, Instant t) {
        int k = 0;
        for (int i = 0; i < starts.size(); i++) if (!starts.get(i).isAfter(t)) k = i;
        return k;
    }

    private static double round(double v, int scale) {
        double f = Math.pow(10, scale);
        return Math.round(v * f) / f;
    }
}
