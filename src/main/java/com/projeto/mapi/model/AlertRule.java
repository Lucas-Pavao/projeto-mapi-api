package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Regra de alerta (ADR 0011). Alvo nulo = todos. */
@Entity
@Table(name = "alert_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertKind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flood_point_id")
    private FloodPoint floodPoint;

    @Column(name = "station_id")
    private String stationId;

    private Double threshold;

    @Column(name = "threshold_source", columnDefinition = "TEXT")
    private String thresholdSource;

    @Column(name = "min_risk")
    private String minRisk;

    @Column(nullable = false)
    private boolean enabled;

    /** Canais separados por vírgula; hoje só BROWSER. */
    @Column(nullable = false)
    @Builder.Default
    private String channels = "BROWSER";

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
