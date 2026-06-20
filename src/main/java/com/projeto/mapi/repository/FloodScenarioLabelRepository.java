package com.projeto.mapi.repository;

import com.projeto.mapi.model.FloodScenarioLabel;
import com.projeto.mapi.model.FloodScenarioLabelId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FloodScenarioLabelRepository extends JpaRepository<FloodScenarioLabel, FloodScenarioLabelId> {
}
