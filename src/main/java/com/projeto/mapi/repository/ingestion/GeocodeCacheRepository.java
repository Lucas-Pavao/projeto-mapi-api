package com.projeto.mapi.repository.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Cache versionado da geocodificação (V28): uma linha ativa por (endereço, bairro, cidade). */
@Repository
@RequiredArgsConstructor
public class GeocodeCacheRepository {

    public record Entry(double lat, double lon, String level, String provider, String origin, int version) {}

    /** Ocorrência já geocodificada, para a primeira carga do cache. */
    public record Geocoded(String addressText, String neighborhood, double lat, double lon, String level, Instant at) {}

    private final JdbcTemplate jdbc;

    public Optional<Entry> findActive(String addressKey, String neighborhoodKey, String cityKey) {
        return jdbc.query("SELECT lat, lon, geocode_level, provider, origin, version FROM geocode_cache "
                        + "WHERE address_key = ? AND neighborhood_key = ? AND city_key = ? AND active",
                (rs, i) -> new Entry(rs.getDouble(1), rs.getDouble(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getInt(6)), addressKey, neighborhoodKey, cityKey).stream().findFirst();
    }

    /** Grava a versão 1 se a chave ainda não existe; devolve se gravou. */
    public boolean insertIfAbsent(String addressKey, String neighborhoodKey, String cityKey, double lat, double lon,
                                  String level, String provider, String origin, Instant geocodedAt) {
        return jdbc.update("INSERT INTO geocode_cache (address_key, neighborhood_key, city_key, version, lat, lon, "
                        + "geocode_level, provider, origin, geocoded_at) SELECT ?, ?, ?, 1, ?, ?, ?, ?, ?, ? WHERE NOT EXISTS "
                        + "(SELECT 1 FROM geocode_cache WHERE address_key = ? AND neighborhood_key = ? AND city_key = ?)",
                addressKey, neighborhoodKey, cityKey, lat, lon, level, provider, origin,
                OffsetDateTime.ofInstant(geocodedAt, ZoneOffset.UTC), addressKey, neighborhoodKey, cityKey) > 0;
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM geocode_cache", Long.class);
        return n == null ? 0 : n;
    }

    /** Ocorrências com coordenada vinda do geocodificador, da mais antiga para a mais nova (a primeira de cada chave vale). */
    public List<Geocoded> geocodedOccurrences() {
        return jdbc.query("""
                SELECT o.address_text, o.neighborhood, o.geocoded_lat, o.geocoded_lon, o.geocode_level, r.started_at
                FROM flood_occurrence o JOIN ingestion_run r ON r.id = o.run_id
                WHERE o.geocoded_lat IS NOT NULL AND o.geocoded_lon IS NOT NULL AND o.geocode_method = 'GEOCODING_SERVICE'
                ORDER BY o.id
                """, (rs, i) -> new Geocoded(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4),
                rs.getString(5), rs.getObject(6, OffsetDateTime.class).toInstant()));
    }
}
