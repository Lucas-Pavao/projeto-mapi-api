package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "flood_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloodEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flood_point_id", nullable = false)
    private FloodPoint floodPoint;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "confirmed_by")
    private String confirmedBy;

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
