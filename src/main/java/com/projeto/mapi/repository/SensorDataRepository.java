package com.projeto.mapi.repository;

import com.projeto.mapi.model.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SensorDataRepository extends JpaRepository<SensorData, Long> {
    List<SensorData> findBySensorIdOrderByTimestampDesc(String sensorId);

    @Query("SELECT s FROM SensorData s WHERE s.id IN (SELECT MAX(s2.id) FROM SensorData s2 GROUP BY s2.sensorId)")
    List<SensorData> findAllLatest();

    Optional<SensorData> findBySensorIdAndTimestamp(String sensorId, LocalDateTime timestamp);
}
