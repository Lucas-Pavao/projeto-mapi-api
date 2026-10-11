package com.projeto.mapi.repository;

import com.projeto.mapi.model.FloodPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FloodPredictionRepository extends JpaRepository<FloodPrediction, Long> {

    /** Última previsão gravada para a coordenada do ponto (qualquer status). */
    Optional<FloodPrediction> findFirstByLatitudeAndLongitudeOrderByTimestampDesc(Double latitude, Double longitude);

    /** Última previsão {@code SUCCESS} da coordenada: fonte de {@code lastValidIssuedAt}. */
    Optional<FloodPrediction> findFirstByLatitudeAndLongitudeAndStatusOrderByTimestampDesc(
            Double latitude, Double longitude, String status);

    /** Última previsão do ponto (qualquer status), pela coluna {@code flood_point_id}. */
    Optional<FloodPrediction> findFirstByFloodPointIdOrderByTimestampDesc(Long floodPointId);

    /** Última previsão {@code SUCCESS} do ponto: fonte de {@code lastValidIssuedAt}. */
    Optional<FloodPrediction> findFirstByFloodPointIdAndStatusOrderByTimestampDesc(Long floodPointId, String status);
}
