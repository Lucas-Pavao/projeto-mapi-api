package com.projeto.mapi.service.terrain.impl;

import com.projeto.mapi.repository.terrain.TerrainRepository;
import com.projeto.mapi.repository.terrain.TerrainRepository.PointRow;
import com.projeto.mapi.service.terrain.TerrainExportColumns;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TerrainExportColumnsImpl implements TerrainExportColumns {

    static final String HEADER = ",terrain_run_id,terrain_street_elevation_m,terrain_mean_slope_pct,"
            + "terrain_depression_depth_m,terrain_contributing_area_m2";
    private static final String EMPTY = ",,,,,";

    private final TerrainRepository repository;

    @Override
    public Appended append(String csv) {
        var run = repository.currentRun();
        Map<String, PointRow> bySlug = new HashMap<>();
        if (run.isPresent()) {
            for (PointRow p : repository.allCurrentPoints()) bySlug.put(p.slug(), p);
        }
        String[] lines = csv.split("\n", -1);
        StringBuilder out = new StringBuilder(csv.length() + lines.length * 24);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i == lines.length - 1 && line.isEmpty()) break; // CSV termina em \n
            out.append(line).append(i == 0 ? HEADER : columns(bySlug.get(line.substring(0, Math.max(0, line.indexOf(','))))))
                    .append('\n');
        }
        return new Appended(out.toString(),
                run.map(r -> new Provenance(r.id(), r.vrtSha256())).orElse(null));
    }

    private static String columns(PointRow p) {
        if (p == null || !"OK".equals(p.status())) return EMPTY;
        return "," + p.runId() + "," + p.streetElevationM() + "," + p.meanSlopePct() + "," + p.depressionDepthM()
                + "," + p.contributingAreaM2();
    }
}
