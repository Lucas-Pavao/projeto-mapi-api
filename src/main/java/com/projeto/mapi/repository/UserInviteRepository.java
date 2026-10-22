package com.projeto.mapi.repository;

import com.projeto.mapi.model.UserInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserInviteRepository extends JpaRepository<UserInvite, Long> {

    Optional<UserInvite> findByTokenHash(String tokenHash);

    /** Consome o convite de forma atômica: só uma chamada concorrente vê {@code 1}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserInvite i SET i.usedAt = :now, i.usedBy = :usedBy "
            + "WHERE i.tokenHash = :hash AND i.usedAt IS NULL AND i.expiresAt > :now")
    int consume(@Param("hash") String hash, @Param("now") Instant now, @Param("usedBy") String usedBy);
}
