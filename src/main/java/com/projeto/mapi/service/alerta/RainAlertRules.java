package com.projeto.mapi.service.alerta;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Regras do alerta por ponto (ADR 0026), sem Spring e sem banco: dado o que se sabe no ciclo e o estado anterior, devolve
 * o novo estado e as transições. Dois campos com fontes e falhas diferentes:
 * <ul>
 *   <li>{@code rain_alert}: ALERTA se a chuva medida no ponto na duração Te passa da capacidade da galeria (IDF na
 *   duração Te, TR configurável); ATENCAO se a probabilidade de evento FORTE em 1 h do rain_v1 passa de L_liga;</li>
 *   <li>{@code point_flood}: PROVAVEL se a lâmina do balde a 10 min chega a h_crit agora ou, na projeção com o quantil
 *   de 50%, em até 60 min.</li>
 * </ul>
 * Falta de dado vira SEM_DADO com motivo, nunca NENHUM nem NAO. Um ALERTA medido sai mesmo com a IA fora.
 */
public final class RainAlertRules {

    private RainAlertRules() {
    }

    /** Intensidade de projeto, mm/h. Silva e Araújo (2013), Eq. 16 (PARAMETROS-MODELO-FISICO.md, item 6). */
    public static double idfMmH(double trYears, double minutes) {
        return 1380.2176 * Math.pow(trYears, 0.19369) / Math.pow(minutes + 22.0, 0.78201);
    }

    /** Lâmina de chuva (mm) que a galeria escoa na duração Te: i(TR, Te) x Te / 60. */
    public static double capacityMm(double trYears, double teMinutes) {
        return idfMmH(trYears, teMinutes) * teMinutes / 60.0;
    }

    public record Params(double probOn, double probOff, int minDurationMinutes, int expiryMinutes, int cancelDryMinutes,
                         double dryMm, int eventGapMinutes, int freshnessMinutes, double trYears, double teMinutes,
                         double hcritMm, int horizonMinutes) {}

    /** Previsão do rain_v1 no ciclo. {@code failure} não nulo = sem previsão. */
    public record Forecast(Double probForte1h, Double q50_1h, Double q80_1h, Double threshold, String modelVersion,
                           SemDadoMotivo failure) {
        public static Forecast failed(SemDadoMotivo m) {
            return new Forecast(null, null, null, null, null, m);
        }
    }

    /** Resultado do balde no ciclo. {@code failure} não nulo = sem lâmina. */
    public record Bucket(Double hNowMm, Integer minutesToHcritQ50, Integer minutesToHcritQ80, Double hProjMaxQ50Mm,
                         Double hProjMaxQ80Mm, String version, SemDadoMotivo failure, Limpeza limpeza) {
        public Bucket(Double hNowMm, Integer minutesToHcritQ50, Integer minutesToHcritQ80, Double hProjMaxQ50Mm,
                      Double hProjMaxQ80Mm, String version, SemDadoMotivo failure) {
            this(hNowMm, minutesToHcritQ50, minutesToHcritQ80, hProjMaxQ50Mm, hProjMaxQ80Mm, version, failure, null);
        }

        public static Bucket failed(SemDadoMotivo m) {
            return new Bucket(null, null, null, null, null, null, m, null);
        }
    }

    /**
     * Limpeza recente (docs/MODELO-DISPONIBILIDADE-GALERIA.md, seção 8): probabilidade de a lâmina chegar a h_crit
     * misturando galeria livre e entupida, com P(entupida) = 1 - exp(-lambda_re · dias desde a última limpeza da 156).
     * Liga PROVAVEL quando a probabilidade >= {@code probOn}. Nulo = sem dado de limpeza (regra de sempre).
     */
    public record Limpeza(Double diasDesdeLimpeza, Double pEntupida, Double probAgora, Double prob60Q50,
                          Integer minutesToHcritQ50Entupida, double probOn) {}

    /**
     * Medida no ciclo: leitura mais nova (das estações a até 5 km que passaram no controle), chuva na duração Te e nos
     * últimos 30 min ({@code null} se o passo não tem estação) e se a fonte da chuva está parada.
     */
    public record Measured(Instant newestReading, Double rainTeMm, Double rainCancelWindowMm, Double rainLastStepMm,
                           boolean sourceStale) {}

    public record Previous(RainAlertLevel rain, PointFloodLevel flood, Instant rainSince, Instant lastRenewedAt,
                           UUID eventId, Instant eventLastActiveAt) {
        public static Previous initial() {
            return new Previous(null, null, null, null, null, null);
        }
    }

    public record Transition(String field, String from, String to, TransitionKind kind, String reason) {}

    public record Decision(RainAlertLevel rain, SemDadoMotivo rainReason, PointFloodLevel flood, SemDadoMotivo floodReason,
                           Instant rainSince, Instant lastRenewedAt, UUID eventId, Instant eventLastActiveAt,
                           Integer floodInMinutes, String trigger, List<Transition> transitions, Double ageMinutes) {}

    public static Decision decide(Instant now, Measured m, Forecast f, Bucket b, TerrainStatus terrain, Previous prev,
                                  Params p) {
        Double age = m.newestReading() == null ? null : Duration.between(m.newestReading(), now).toSeconds() / 60.0;
        SemDadoMotivo measuredFailure = null;
        if (m.newestReading() == null || age > 70) measuredFailure = SemDadoMotivo.SEM_ESTACAO_VALIDA;
        else if (m.sourceStale()) measuredFailure = SemDadoMotivo.FONTE_PARADA;
        else if (age > p.freshnessMinutes()) measuredFailure = SemDadoMotivo.LEITURA_VELHA;
        else if (m.rainTeMm() == null) measuredFailure = SemDadoMotivo.SEM_ESTACAO_VALIDA;

        // ---------------- rain_alert ----------------
        RainAlertLevel prevRain = prev.rain() == null ? RainAlertLevel.NENHUM : prev.rain();
        boolean alerta = measuredFailure == null && m.rainTeMm() >= capacityMm(p.trYears(), p.teMinutes());
        double probOn = f.threshold() != null && Double.isNaN(p.probOn()) ? f.threshold() : p.probOn();
        double probOff = Double.isNaN(p.probOff()) ? probOn : p.probOff();
        RainAlertLevel cand;
        SemDadoMotivo rainReason = null;
        boolean renewed = false;
        boolean dry = m.rainCancelWindowMm() != null && m.rainCancelWindowMm() < p.dryMm();
        String trigger = null;
        if (alerta) {
            cand = RainAlertLevel.ALERTA;
            renewed = true;
            trigger = "chuva medida em " + (int) p.teMinutes() + " min acima da galeria";
        } else if (measuredFailure != null) {
            cand = RainAlertLevel.SEM_DADO;
            rainReason = measuredFailure;
        } else if (f.failure() != null) {
            cand = RainAlertLevel.SEM_DADO;
            rainReason = f.failure();
        } else if (f.probForte1h() >= probOn) {
            cand = RainAlertLevel.ATENCAO;
            renewed = true;
            trigger = "probabilidade de FORTE em 1 h acima de L_liga";
        } else if (prevRain.on() && f.probForte1h() >= probOff) {
            cand = RainAlertLevel.ATENCAO;                         // histerese: mantém sem renovar
            trigger = "histerese (acima de L_desliga)";
        } else {
            cand = RainAlertLevel.NENHUM;
        }
        Instant lastRenewed = renewed ? now : prev.lastRenewedAt();
        TransitionKind offKind = null;
        if (prevRain.on() && cand == RainAlertLevel.NENHUM) {
            offKind = dry ? TransitionKind.CANCELA : TransitionKind.DESLIGA;
            // duração mínima segura o DESLIGA, não o cancelamento por tempo seco nem o SEM_DADO
            if (offKind == TransitionKind.DESLIGA && prev.rainSince() != null
                    && Duration.between(prev.rainSince(), now).toMinutes() < p.minDurationMinutes()) {
                cand = prevRain;
                offKind = null;
            }
        }
        if (cand.on() && !renewed && lastRenewed != null && Duration.between(lastRenewed, now).toMinutes() >= p.expiryMinutes()) {
            cand = RainAlertLevel.NENHUM;
            offKind = TransitionKind.EXPIRA;
        }
        if (cand == RainAlertLevel.ATENCAO && prevRain == RainAlertLevel.ALERTA && !alerta && measuredFailure == null
                && f.failure() != null) {
            cand = RainAlertLevel.SEM_DADO;                        // caiu o medido e não há previsão: sem dado
            rainReason = f.failure();
        }

        // ---------------- point_flood ----------------
        PointFloodLevel prevFlood = prev.flood() == null ? PointFloodLevel.NAO : prev.flood();
        PointFloodLevel flood;
        SemDadoMotivo floodReason = null;
        Integer floodIn = null;
        if (terrain == TerrainStatus.SEM_TERRENO) {
            flood = PointFloodLevel.SEM_DADO;
            floodReason = SemDadoMotivo.SEM_TERRENO;
        } else if (terrain == TerrainStatus.EM_REVISAO) {
            flood = PointFloodLevel.SEM_DADO;
            floodReason = SemDadoMotivo.TERRENO_EM_REVISAO;
        } else if (measuredFailure != null) {
            flood = PointFloodLevel.SEM_DADO;
            floodReason = measuredFailure;
        } else if (b.failure() != null) {
            flood = PointFloodLevel.SEM_DADO;
            floodReason = b.failure();
        } else if (b.hNowMm() != null && b.hNowMm() >= p.hcritMm()) {
            flood = PointFloodLevel.PROVAVEL;
            floodIn = 0;
        } else if (b.limpeza() != null && b.limpeza().probAgora() != null && b.limpeza().probAgora() >= b.limpeza().probOn()) {
            flood = PointFloodLevel.PROVAVEL;
            floodIn = 0;
        } else if (f.failure() != null || b.hProjMaxQ50Mm() == null) {
            flood = PointFloodLevel.SEM_DADO;
            floodReason = f.failure() != null ? SemDadoMotivo.SEM_PREVISAO : SemDadoMotivo.SEM_PREVISAO;
        } else if (b.minutesToHcritQ50() != null && b.minutesToHcritQ50() <= p.horizonMinutes()) {
            flood = PointFloodLevel.PROVAVEL;
            floodIn = b.minutesToHcritQ50();
        } else if (b.limpeza() != null && b.limpeza().prob60Q50() != null && b.limpeza().prob60Q50() >= b.limpeza().probOn()) {
            flood = PointFloodLevel.PROVAVEL;
            floodIn = b.limpeza().minutesToHcritQ50Entupida();
        } else {
            flood = PointFloodLevel.NAO;
        }

        // ---------------- evento ----------------
        boolean wet = m.rainLastStepMm() != null && m.rainLastStepMm() >= p.dryMm();
        boolean active = cand.on() || flood == PointFloodLevel.PROVAVEL;
        UUID eventId = prev.eventId();
        Instant lastActive = prev.eventLastActiveAt();
        boolean startsSomething = (cand.on() && !prevRain.on()) || (flood == PointFloodLevel.PROVAVEL && prevFlood != PointFloodLevel.PROVAVEL);
        if (startsSomething && (eventId == null || lastActive == null
                || Duration.between(lastActive, now).toMinutes() >= p.eventGapMinutes())) {
            eventId = UUID.randomUUID();
        }
        if (active || (wet && eventId != null && lastActive != null
                && Duration.between(lastActive, now).toMinutes() < p.eventGapMinutes())) {
            lastActive = now;
        }

        // ---------------- transições ----------------
        List<Transition> tr = new ArrayList<>();
        if (prev.rain() == null || cand != prevRain || (cand == RainAlertLevel.SEM_DADO && prev.rain() == null)) {
            if (prev.rain() != null || cand != RainAlertLevel.NENHUM) {
                tr.add(new Transition("RAIN", prev.rain() == null ? null : prevRain.name(), cand.name(),
                        kindOf(prevRain, cand, offKind), rainReason == null ? null : rainReason.name()));
            }
        }
        if (prev.flood() == null ? flood != PointFloodLevel.NAO : flood != prevFlood) {
            TransitionKind k = flood == PointFloodLevel.SEM_DADO ? TransitionKind.SEM_DADO
                    : flood == PointFloodLevel.PROVAVEL ? TransitionKind.LIGA
                    : prevFlood == PointFloodLevel.SEM_DADO ? TransitionKind.VOLTA_DADO : TransitionKind.DESLIGA;
            tr.add(new Transition("FLOOD", prev.flood() == null ? null : prevFlood.name(), flood.name(), k,
                    floodReason == null ? null : floodReason.name()));
        }
        Instant since = cand == prevRain && prev.rainSince() != null ? prev.rainSince() : now;
        return new Decision(cand, rainReason, flood, floodReason, since, cand.on() ? lastRenewed : null, eventId, lastActive,
                floodIn, trigger, tr, age);
    }

    static TransitionKind kindOf(RainAlertLevel from, RainAlertLevel to, TransitionKind offKind) {
        if (to == RainAlertLevel.SEM_DADO) return TransitionKind.SEM_DADO;
        if (from == RainAlertLevel.SEM_DADO) return to.on() ? TransitionKind.LIGA : TransitionKind.VOLTA_DADO;
        if (!from.on() && to.on()) return TransitionKind.LIGA;
        if (from == RainAlertLevel.ATENCAO && to == RainAlertLevel.ALERTA) return TransitionKind.SOBE;
        if (from == RainAlertLevel.ALERTA && to == RainAlertLevel.ATENCAO) return TransitionKind.DESCE;
        return offKind != null ? offKind : TransitionKind.DESLIGA;
    }

    /** Situação do terreno do ponto para o balde. */
    public enum TerrainStatus { OK, SEM_TERRENO, EM_REVISAO }
}
