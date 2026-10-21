package com.projeto.mapi.repository;

import com.projeto.mapi.model.FloodPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * Série do ponto em [from, to) por {@code issued_at}. O filtro extra em {@code timestamp} (coluna sem fuso,
     * ADR 0001 pendente) só poda chunks da hypertable: o chamador passa a janela alargada em 1 dia.
     */
    @Query(value = "SELECT * FROM flood_predictions WHERE flood_point_id = :pointId "
            + "AND timestamp >= :tsFrom AND timestamp <= :tsTo "
            + "AND issued_at >= :from AND issued_at < :to ORDER BY issued_at ASC", nativeQuery = true)
    List<FloodPrediction> findSeries(@Param("pointId") Long pointId,
                                     @Param("from") Instant from, @Param("to") Instant to,
                                     @Param("tsFrom") LocalDateTime tsFrom, @Param("tsTo") LocalDateTime tsTo);

    /** Última previsão SUCCESS anterior a {@code before}, olhando até {@code tsFloor} para podar chunks. */
    @Query(value = "SELECT * FROM flood_predictions WHERE flood_point_id = :pointId AND status = 'SUCCESS' "
            + "AND timestamp >= :tsFloor AND issued_at < :before ORDER BY issued_at DESC LIMIT 1", nativeQuery = true)
    Optional<FloodPrediction> findLastSuccessBefore(@Param("pointId") Long pointId, @Param("before") Instant before,
                                                    @Param("tsFloor") LocalDateTime tsFloor);
}
