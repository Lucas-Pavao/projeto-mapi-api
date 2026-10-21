package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

/** Estado do ciclo anterior de um par (regra, alvo): base da regra "só alerta na transição". */
@Entity
@Table(name = "alert_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(AlertState.Key.class)
public class AlertState {

    @Id
    @Column(name = "rule_id")
    private Long ruleId;

    @Id
    @Column(name = "target_key")
    private String targetKey;

    @Column(name = "in_condition", nullable = false)
    private boolean inCondition;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long ruleId;
        private String targetKey;
    }
}
