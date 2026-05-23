package com.projeto.mapi.repository;

import com.projeto.mapi.model.FloodPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FloodPointRepository extends JpaRepository<FloodPoint, Long> {
    List<FloodPoint> findByActiveTrue();
    java.util.Optional<FloodPoint> findBySlug(String slug);
}
