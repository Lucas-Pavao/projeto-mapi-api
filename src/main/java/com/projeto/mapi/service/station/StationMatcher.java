package com.projeto.mapi.service.station;

import com.projeto.mapi.util.GeoUtils;
import com.projeto.mapi.util.RmrFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regra pura da unificação de estações (ADR 0023), sem banco nem Spring.
 *
 * <p>A fonte preferida ({@code CEMADEN_PED}) define as estações canônicas: duas estações da mesma fonte nunca se fundem,
 * porque o cadastro da própria fonte já as distingue. Cada estação da outra fonte vira apelido da canônica mais
 * próxima, se ela estiver a até {@code maxDistanceM}; senão, vira canônica sozinha. A semelhança de nome é calculada e
 * devolvida para o registro, mas não entra na decisão.
 */
public final class StationMatcher {

    public record Station(String source, String stationId, String name, double lat, double lon, String municipality) {}

    public record Alias(Station station, double distanceM, Double nameSimilarity) {}

    public record Canonical(String canonicalId, Station primary, List<Alias> aliases) {}

    private StationMatcher() {
    }

    public static List<Canonical> unify(List<Station> preferred, List<Station> others, double maxDistanceM) {
        Map<String, Canonical> byId = new LinkedHashMap<>();
        for (Station p : preferred) {
            List<Alias> aliases = new ArrayList<>();
            aliases.add(new Alias(p, 0.0, 1.0));
            byId.put(p.stationId(), new Canonical(p.stationId(), p, aliases));
        }
        for (Station o : others) {
            Station nearest = null;
            double best = Double.MAX_VALUE;
            for (Station p : preferred) {
                double d = distanceM(o, p);
                // empate pela igualdade do código (o Cemaden repete o código nas duas rotas), depois pelo código
                if (d < best || (d == best && nearest != null && o.stationId().equals(p.stationId()))) {
                    best = d;
                    nearest = p;
                }
            }
            if (nearest != null && best <= maxDistanceM) {
                byId.get(nearest.stationId()).aliases().add(new Alias(o, best, nameSimilarity(o.name(), nearest.name())));
            } else {
                String id = byId.containsKey(o.stationId()) ? o.source() + ":" + o.stationId() : o.stationId();
                List<Alias> aliases = new ArrayList<>();
                aliases.add(new Alias(o, 0.0, 1.0));
                byId.put(id, new Canonical(id, o, aliases));
            }
        }
        List<Canonical> out = new ArrayList<>(byId.values());
        out.sort(Comparator.comparing(Canonical::canonicalId));
        return out;
    }

    public static double distanceM(Station a, Station b) {
        return GeoUtils.calculateDistance(a.lat(), a.lon(), b.lat(), b.lon()) * 1000.0;
    }

    /**
     * Jaccard das palavras do nome, sem etiquetas entre colchetes ("[CEMADEN]", "[APAC]"), sem acento e sem palavras de
     * uma letra. 1 = mesmas palavras; 0 = nenhuma em comum. Só para o registro.
     */
    public static Double nameSimilarity(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) return null;
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) inter.size() / union.size();
    }

    private static Set<String> tokens(String name) {
        if (name == null) return Set.of();
        String clean = RmrFilter.normalize(name.replaceAll("\\[[^\\]]*\\]", " ")).replaceAll("[^A-Z0-9]+", " ").trim();
        Set<String> out = new HashSet<>();
        Arrays.stream(clean.split(" ")).filter(t -> t.length() > 1).forEach(out::add);
        return out;
    }

    /**
     * Código IBGE de 7 dígitos a partir dos 6 primeiros do código da estação Cemaden (que começa pelo município).
     * Dígito verificador do IBGE: pesos 1,2,1,2,1,2; produto acima de 9 soma os algarismos; DV = (10 - soma % 10) % 10.
     * Conferido: 261160 → 2611606 (Recife), 260345 → 2603454 (Camaragibe).
     */
    public static String ibgeCode(String stationId) {
        if (stationId == null || stationId.length() < 6 || !stationId.substring(0, 6).chars().allMatch(Character::isDigit)) {
            return null;
        }
        String six = stationId.substring(0, 6);
        int sum = 0;
        for (int i = 0; i < 6; i++) {
            int p = (six.charAt(i) - '0') * (i % 2 == 0 ? 1 : 2);
            sum += p > 9 ? p / 10 + p % 10 : p;
        }
        return six + ((10 - sum % 10) % 10);
    }
}
