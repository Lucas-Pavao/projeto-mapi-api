package com.projeto.mapi.repository;

import com.projeto.mapi.model.FloodPrediction;
import com.projeto.mapi.model.FloodPredictionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FloodPredictionRepository extends JpaRepository<FloodPrediction, FloodPredictionId> {
}
