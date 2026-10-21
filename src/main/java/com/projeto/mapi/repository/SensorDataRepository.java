package com.projeto.mapi.repository;

import com.projeto.mapi.model.SensorData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SensorDataRepository extends JpaRepository<SensorData, Long> {
    Page<SensorData> findBySensorIdOrderByTimestampDesc(String sensorId, Pageable pageable);

    Optional<SensorData> findFirstBySensorIdOrderByTimestampDesc(String sensorId);

    @Query(value = "SELECT DISTINCT ON (sensor_id) * FROM sensor_data WHERE timestamp >= :since ORDER BY sensor_id, timestamp DESC", nativeQuery = true)
    List<SensorData> findAllLatest(@org.springframework.data.repository.query.Param("since") LocalDateTime since);

    /** Linha de série agregada por balde de tempo ({@link #rainBuckets}, {@link #riverBuckets}). */
    interface BucketRow {
        String getSensorId();
        java.sql.Timestamp getBucket();
        Double getVal();
    }

    @Query(value = "SELECT sensor_id AS sensorId, time_bucket(CAST(:step AS interval), timestamp) AS bucket, "
            + "MAX(accumulated_precipitation) AS val FROM sensor_data "
            + "WHERE timestamp >= :from AND timestamp < :to AND accumulated_precipitation IS NOT NULL "
            + "GROUP BY sensor_id, bucket ORDER BY sensor_id, bucket", nativeQuery = true)
    List<BucketRow> rainBuckets(@org.springframework.data.repository.query.Param("from") LocalDateTime from,
                                @org.springframework.data.repository.query.Param("to") LocalDateTime to,
                                @org.springframework.data.repository.query.Param("step") String step);

    @Query(value = "SELECT sensor_id AS sensorId, time_bucket(CAST(:step AS interval), timestamp) AS bucket, "
            + "AVG(water_level) AS val FROM sensor_data "
            + "WHERE timestamp >= :from AND timestamp < :to AND water_level IS NOT NULL "
            + "GROUP BY sensor_id, bucket ORDER BY sensor_id, bucket", nativeQuery = true)
    List<BucketRow> riverBuckets(@org.springframework.data.repository.query.Param("from") LocalDateTime from,
                                 @org.springframework.data.repository.query.Param("to") LocalDateTime to,
                                 @org.springframework.data.repository.query.Param("step") String step);

    /** Projeção de {@link #maxRainSince}. */
    interface RainRow {
        String getSensorId();
        Double getMaxMm();
    }

    @Query(value = "SELECT sensor_id AS sensorId, MAX(accumulated_precipitation) AS maxMm FROM sensor_data "
            + "WHERE timestamp >= :since AND accumulated_precipitation IS NOT NULL GROUP BY sensor_id", nativeQuery = true)
    List<RainRow> maxRainSince(@org.springframework.data.repository.query.Param("since") LocalDateTime since);

    Optional<SensorData> findBySensorIdAndTimestamp(String sensorId, LocalDateTime timestamp);

    // Usado por repairSensorCoordinates(): evita carregar a hypertable inteira (>1M linhas) em memória,
    // buscando apenas os registros que realmente precisam de correção de coordenadas.
    List<SensorData> findByLatitudeIsNullOrLongitudeIsNull();

    List<SensorData> findBySensorIdAndTimestampBetween(String sensorId, LocalDateTime start, LocalDateTime end);
    List<SensorData> findBySensorIdInAndTimestampBetween(java.util.Collection<String> sensorIds, LocalDateTime start, LocalDateTime end);
    List<SensorData> findByCodeAndTimestampBetween(String code, LocalDateTime start, LocalDateTime end);
    long countBySensorId(String sensorId);

    @Query("SELECT s FROM SensorData s WHERE " +
           "(6371 * acos(cos(radians(:lat)) * cos(radians(s.latitude)) * cos(radians(s.longitude) - radians(:lon)) + sin(radians(:lat)) * sin(radians(s.latitude)))) <= :radiusKm AND " +
           "s.timestamp BETWEEN :start AND :end ORDER BY s.timestamp ASC")
    List<SensorData> findSensorsByRadius(Double lat, Double lon, Double radiusKm, LocalDateTime start, LocalDateTime end);

    @Query("SELECT s FROM SensorData s WHERE " +
           "(6371 * acos(cos(radians(:lat)) * cos(radians(s.latitude)) * cos(radians(s.longitude) - radians(:lon)) + sin(radians(:lat)) * sin(radians(s.latitude)))) <= :radiusKm AND " +
           "s.timestamp >= :since ORDER BY s.timestamp DESC")
    List<SensorData> findLatestSensorsByRadius(Double lat, Double lon, Double radiusKm, LocalDateTime since);

    List<SensorData> findByCodeOrderByTimestampDesc(String code);
    Optional<SensorData> findFirstByCodeOrderByTimestampDesc(String code);

    @Query("SELECT DISTINCT s.sensorId FROM SensorData s")
    List<String> findDistinctSensorIds();

    @Query(value = "SELECT DISTINCT ON (sensor_id) * FROM sensor_data ORDER BY sensor_id, timestamp DESC",
           countQuery = "SELECT COUNT(DISTINCT sensor_id) FROM sensor_data",
           nativeQuery = true)
    Page<SensorData> findDistinctSensorsWithMetadata(Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT YEAR(s.timestamp), COUNT(s) FROM SensorData s WHERE s.sensorId = :sensorId GROUP BY YEAR(s.timestamp)")
    List<Object[]> countBySensorIdGroupedByYear(String sensorId);
}
