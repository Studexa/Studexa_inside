package com.studexa.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.user.id = :userId and r.revoked = false")
    int revokeAllForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true where r.familyId = :familyId and r.revoked = false")
    int revokeAllForFamily(@Param("familyId") UUID familyId);
}
