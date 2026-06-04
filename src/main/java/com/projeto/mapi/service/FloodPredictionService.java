package com.projeto.mapi.service;

import com.projeto.mapi.dto.FloodPredictionRequestDTO;
import com.projeto.mapi.dto.FloodPredictionResponseDTO;

public interface FloodPredictionService {
    FloodPredictionResponseDTO getPrediction(FloodPredictionRequestDTO request);
}
