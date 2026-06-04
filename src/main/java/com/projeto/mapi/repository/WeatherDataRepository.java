package com.projeto.mapi.repository;

import com.projeto.mapi.model.WeatherData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WeatherDataRepository extends JpaRepository<WeatherData, Long> {
    List<WeatherData> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    List<WeatherData> findByLatitudeAndLongitudeAndTimestampBetween(Double lat, Double lon, LocalDateTime start, LocalDateTime end);
    long countByLatitudeAndLongitude(Double lat, Double lon);

    @org.springframework.data.jpa.repository.Query("SELECT YEAR(w.timestamp), COUNT(w) FROM WeatherData w WHERE w.latitude = :lat AND w.longitude = :lon GROUP BY YEAR(w.timestamp)")
    List<Object[]> countByLatitudeAndLongitudeGroupedByYear(Double lat, Double lon);
}
