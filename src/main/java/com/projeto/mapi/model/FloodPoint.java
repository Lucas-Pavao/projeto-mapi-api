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

    private String name;
    private String description;
    
    private Double latitude;
    private Double longitude;

    @Column(name = "alert_threshold_mm")
    private Double alertThresholdMm;

    @Builder.Default
    private Boolean active = true;
}
