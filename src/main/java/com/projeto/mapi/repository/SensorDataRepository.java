package com.projeto.mapi.repository;

import com.projeto.mapi.model.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SensorDataRepository extends JpaRepository<SensorData, Long> {
    List<SensorData> findBySensorIdOrderByTimestampDesc(String sensorId);
}
