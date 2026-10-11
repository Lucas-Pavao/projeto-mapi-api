package com.projeto.mapi.repository.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.service.ingestion.StationInfo;
import com.projeto.mapi.service.ingestion.validation.SourceTzRule;
import com.projeto.mapi.service.ingestion.validation.VariableRule;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Leitura do source_registry e do variable_catalog; upsert de station. */
@Repository
@RequiredArgsConstructor
public class SourceRegistryRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public Optional<SourceTzRule> findTz(String source) {
        return jdbc.query("SELECT source_tz, verification_state FROM source_registry WHERE source = ?",
                (rs, i) -> new SourceTzRule(rs.getString(1), rs.getString(2)), source).stream().findFirst();
    }

    public Map<String, VariableRule> catalog() {
        Map<String, VariableRule> map = new HashMap<>();
        jdbc.query("SELECT * FROM variable_catalog", rs -> {
            Double min = rs.getObject("min_value", Double.class);
            Double max = rs.getObject("max_value", Double.class);
            map.put(rs.getString("variable"), new VariableRule(rs.getString("variable"),
                    rs.getString("canonical_unit"), min, max, rs.getString("range_source"),
                    rs.getString("expected_resolution"), rs.getString("interval_convention"),
                    rs.getString("hourly_aggregation"), rs.getDouble("min_completeness")));
        });
        return map;
    }

    public void upsertStations(List<StationInfo> stations) {
        if (stations.isEmpty()) return;
        jdbc.batchUpdate("""
                INSERT INTO station (source, station_id, name, lat, lon, municipality, attrs)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source, station_id) DO UPDATE SET name = EXCLUDED.name, lat = EXCLUDED.lat,
                  lon = EXCLUDED.lon, municipality = EXCLUDED.municipality, attrs = EXCLUDED.attrs
                """, stations, 200, (ps, s) -> {
            ps.setString(1, s.source());
            ps.setString(2, s.stationId());
            ps.setString(3, s.name());
            ps.setObject(4, s.lat());
            ps.setObject(5, s.lon());
            ps.setString(6, s.municipality());
            try {
                ps.setObject(7, mapper.writeValueAsString(s.attrs() == null ? mapper.createObjectNode() : s.attrs()),
                        Types.OTHER);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
