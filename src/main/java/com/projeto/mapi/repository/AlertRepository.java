package com.projeto.mapi.repository;

import com.projeto.mapi.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;

public interface AlertRepository extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {

    boolean existsByRule_IdAndFloodPoint_IdAndTriggeredAtGreaterThanEqual(Long ruleId, Long pointId, Instant since);

    boolean existsByRule_IdAndStationIdAndTriggeredAtGreaterThanEqual(Long ruleId, String stationId, Instant since);
}
