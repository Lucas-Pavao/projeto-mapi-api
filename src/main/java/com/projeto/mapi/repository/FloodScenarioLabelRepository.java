package com.projeto.mapi.repository;

import com.projeto.mapi.model.FloodScenarioLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FloodScenarioLabelRepository extends JpaRepository<FloodScenarioLabel, Long> {
}
