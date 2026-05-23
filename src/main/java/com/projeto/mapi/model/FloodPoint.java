package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "flood_points")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloodPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(nullable = false)
    private String name;
    
    private String municipality;
    private String description;
    
    @Column(nullable = false)
    private Double latitude;
    
    @Column(nullable = false)
    private Double longitude;

    private Double altitudeM;
    private Double distanceToChannelM;

    private String pluviometerStationId;
    private String riverLevelStationId;
    private String basinName;

    @Column(name = "alert_threshold_mm")
    private Double alertThresholdMm;

    @Builder.Default
    private Boolean active = true;
}
