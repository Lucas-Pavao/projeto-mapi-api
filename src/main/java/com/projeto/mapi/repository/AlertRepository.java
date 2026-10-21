package com.projeto.mapi.repository;

import com.projeto.mapi.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Collection;

public interface AlertRepository extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {

    /** Já houve alerta da regra para o ponto, com uma dessas severidades, desde {@code since}? */
    boolean existsByRule_IdAndFloodPoint_IdAndSeverityInAndTriggeredAtGreaterThanEqual(
            Long ruleId, Long pointId, Collection<Alert.Severity> severities, Instant since);

    boolean existsByRule_IdAndStationIdAndSeverityInAndTriggeredAtGreaterThanEqual(
            Long ruleId, String stationId, Collection<Alert.Severity> severities, Instant since);
}
