package com.projeto.mapi.service.sensor;

import com.projeto.mapi.repository.ingestion.ObservationReadRepository.HourlyRainRow;
import com.projeto.mapi.util.GeoUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Controle espacial da chuva horária: pico isolado de um pluviômetro, sem chuva nos vizinhos, sai da série. Caso que
 * motivou (2026-10-21): Bonsucesso (Olinda) marcou 40,7 mm numa hora em que as 15 estações num raio de 6,5 km
 * marcaram 0, e parou de transmitir; nem consta do painel da CEMADEN. Na média das 48 estações, virou o pico do dia.
 *
 * <p>Regra: a hora de uma estação com {@value #SUSPECT_MM} mm ou mais é suspeita quando pelo menos
 * {@value #MIN_NEIGHBORS} outras estações a até {@value #RADIUS_KM} km informam a mesma hora e todas ficam abaixo de
 * {@value #NEIGHBOR_DRY_MM} mm. Nada é apagado de {@code observation}; só deixa de entrar na série servida.
 */
public final class RainSpatialCheck {

    public static final double SUSPECT_MM = 10.0;
    public static final double RADIUS_KM = 6.0;
    public static final double NEIGHBOR_DRY_MM = 1.0;
    public static final int MIN_NEIGHBORS = 2;

    private RainSpatialCheck() {
    }

    /** As linhas que passam, na ordem original. Linha sem coordenada não é julgada nem serve de vizinha. */
    public static List<HourlyRainRow> dropIsolatedPeaks(List<HourlyRainRow> rows) {
        List<HourlyRainRow> out = new ArrayList<>(rows.size());
        for (HourlyRainRow r : rows) {
            if (!isIsolatedPeak(r, rows)) out.add(r);
        }
        return out;
    }

    static boolean isIsolatedPeak(HourlyRainRow r, List<HourlyRainRow> all) {
        if (r.mm() < SUSPECT_MM || r.lat() == null || r.lon() == null) return false;
        int neighbors = 0;
        for (HourlyRainRow n : all) {
            if (n == r || n.lat() == null || n.lon() == null || !n.hourEndUtc().equals(r.hourEndUtc())) continue;
            if (n.stationId().equals(r.stationId()) && n.source().equals(r.source())) continue;
            if (GeoUtils.calculateDistance(r.lat(), r.lon(), n.lat(), n.lon()) > RADIUS_KM) continue;
            if (n.mm() >= NEIGHBOR_DRY_MM) return false;
            neighbors++;
        }
        return neighbors >= MIN_NEIGHBORS;
    }
}
