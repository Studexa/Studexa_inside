package com.studexa.quiz;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SessionPlayerRepository extends JpaRepository<SessionPlayer, UUID> {

    List<SessionPlayer> findBySessionId(UUID sessionId);

    List<SessionPlayer> findBySessionIdOrderByTotalScoreDesc(UUID sessionId);

    Optional<SessionPlayer> findBySessionIdAndUserId(UUID sessionId, UUID userId);

    Optional<SessionPlayer> findBySessionIdAndNickname(UUID sessionId, String nickname);

    boolean existsBySessionIdAndNickname(UUID sessionId, String nickname);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SessionPlayer p where p.id = :id")
    Optional<SessionPlayer> findByIdForUpdate(@Param("id") UUID id);
}
