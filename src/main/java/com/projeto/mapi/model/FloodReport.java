package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Relato de alagamento enviado por um usuário e revisado por um OPERADOR. Instantes em UTC (ADR 0001). */
@Entity
@Table(name = "flood_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloodReport {

    public enum Status { PENDING, CONFIRMED, DISCARDED }

    public enum WaterDepth { ANKLE, KNEE, WAIST, ABOVE_WAIST }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "water_depth")
    private WaterDepth waterDepth;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "reported_by", nullable = false)
    private String reportedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nearest_point_id")
    private FloodPoint nearestPoint;

    @Column(name = "nearest_point_distance_m")
    private Integer nearestPointDistanceM;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "flood_event_id")
    private Long floodEventId;

    @Column(name = "discard_reason", columnDefinition = "TEXT")
    private String discardReason;
}
