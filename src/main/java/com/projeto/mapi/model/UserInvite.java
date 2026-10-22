package com.projeto.mapi.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Convite de uso único (ADR 0010). Guarda só o SHA-256 do token. */
@Entity
@Table(name = "user_invite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInvite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String email;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "used_by")
    private String usedBy;
}
