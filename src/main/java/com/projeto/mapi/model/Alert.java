package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Alerta disparado por uma regra (ADR 0011). Severidade do alerta não é classe de risco. */
@Entity
@Table(name = "alert")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    public enum Severity { INFO, WARNING, CRITICAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private AlertRule rule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flood_point_id")
    private FloodPoint floodPoint;

    @Column(name = "station_id")
    private String stationId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "observed_value")
    private Double observedValue;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "degradation_reason")
    private String degradationReason;

    @Column(name = "prediction_id")
    private Long predictionId;

    @Column(name = "model_validation_status")
    private String modelValidationStatus;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;
}
