package com.projeto.mapi.service.ingestion.occurrence;

import com.projeto.mapi.util.GeoUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Deduplicação de ocorrências entre fontes (ADR 0019, item 3). Lógica pura.
 *
 * <p>Dois registros são o mesmo alagamento se têm o mesmo tipo, o mesmo dia civil em America/Recife e estão a até
 * {@code maxDistanceM} um do outro (Haversine de {@link GeoUtils}) (ligação simples: A~B e B~C põem A, B e C no mesmo grupo). Registro sem
 * coordenada fica sozinho (não há como saber se é o mesmo lugar). O grupo herda a melhor precisão temporal, a melhor
 * precisão espacial e a maior confiança dos membros; a coordenada é a do membro com melhor precisão espacial.
 */
public final class OccurrenceClusterer {

    public static final String RULE_VERSION = "cluster-v1";
    public static final double DEFAULT_MAX_DISTANCE_M = 200.0;
    private static final ZoneId RECIFE = ZoneId.of("America/Recife");

    private OccurrenceClusterer() {
    }

    public record Occurrence(long id, String source, String eventType, Instant from, Double lat, Double lon,
                             String temporalPrecision, String spatialPrecision, String confidence) {}

    public record Cluster(LocalDate localDay, String eventType, Double lat, Double lon, String temporalPrecision,
                          String spatialPrecision, String confidence, Instant firstFrom, List<String> sources,
                          List<Long> memberIds) {}

    private static final List<String> TEMPORAL = List.of("MINUTE", "HOUR", "DAY");
    private static final List<String> SPATIAL = List.of("COORDENADA", "LOGRADOURO", "BAIRRO", "MUNICIPIO");
    private static final List<String> CONFIDENCE = List.of("ALTA", "MEDIA", "BAIXA");

    public static List<Cluster> cluster(List<Occurrence> occurrences, double maxDistanceM) {
        List<Occurrence> sorted = new ArrayList<>(occurrences);
        sorted.sort(Comparator.comparingLong(Occurrence::id));
        Map<String, List<Occurrence>> byKey = new LinkedHashMap<>();
        for (Occurrence o : sorted) {
            byKey.computeIfAbsent(o.eventType() + "|" + o.from().atZone(RECIFE).toLocalDate(), k -> new ArrayList<>())
                    .add(o);
        }
        List<Cluster> out = new ArrayList<>();
        for (List<Occurrence> group : byKey.values()) {
            int n = group.size();
            int[] parent = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    Occurrence a = group.get(i), b = group.get(j);
                    if (a.lat() == null || a.lon() == null || b.lat() == null || b.lon() == null) continue;
                    if (GeoUtils.calculateDistance(a.lat(), a.lon(), b.lat(), b.lon()) * 1000 <= maxDistanceM) union(parent, i, j);
                }
            }
            Map<Integer, List<Occurrence>> parts = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) parts.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(group.get(i));
            for (List<Occurrence> members : parts.values()) out.add(merge(members));
        }
        out.sort(Comparator.comparing(Cluster::localDay).thenComparing(c -> c.memberIds().get(0)));
        return out;
    }

    private static Cluster merge(List<Occurrence> members) {
        Occurrence bestSpatial = members.stream()
                .min(Comparator.comparingInt((Occurrence o) -> rank(SPATIAL, o.spatialPrecision()))
                        .thenComparingLong(Occurrence::id)).orElseThrow();
        String temporal = members.stream().map(Occurrence::temporalPrecision)
                .min(Comparator.comparingInt(t -> rank(TEMPORAL, t))).orElseThrow();
        String confidence = members.stream().map(Occurrence::confidence)
                .min(Comparator.comparingInt(c -> rank(CONFIDENCE, c))).orElseThrow();
        Instant first = members.stream().map(Occurrence::from).min(Comparator.naturalOrder()).orElseThrow();
        return new Cluster(members.get(0).from().atZone(RECIFE).toLocalDate(), members.get(0).eventType(),
                bestSpatial.lat(), bestSpatial.lon(), temporal, bestSpatial.spatialPrecision(), confidence, first,
                List.copyOf(new TreeSet<>(members.stream().map(Occurrence::source).toList())),
                members.stream().map(Occurrence::id).sorted().toList());
    }

    private static int rank(List<String> order, String v) {
        int i = v == null ? -1 : order.indexOf(v);
        return i < 0 ? order.size() : i;
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; }
        return i;
    }

    private static void union(int[] p, int a, int b) {
        int ra = find(p, a), rb = find(p, b);
        if (ra != rb) p[Math.max(ra, rb)] = Math.min(ra, rb);
    }
}
