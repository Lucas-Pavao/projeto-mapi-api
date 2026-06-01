package com.projeto.mapi.repository;

import com.projeto.mapi.model.FloodEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

import com.projeto.mapi.model.FloodPoint;
import java.time.LocalDateTime;

@Repository
public interface FloodEventRepository extends JpaRepository<FloodEvent, Long> {
    List<FloodEvent> findByFloodPointId(Long floodPointId);
    long countByFloodPointId(Long floodPointId);
    boolean existsByFloodPointAndStartTime(FloodPoint floodPoint, LocalDateTime startTime);
}
