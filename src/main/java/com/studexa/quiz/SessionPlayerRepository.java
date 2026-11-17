package com.studexa.quiz;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionPlayerRepository extends JpaRepository<SessionPlayer, UUID> {

    List<SessionPlayer> findBySessionId(UUID sessionId);

    List<SessionPlayer> findBySessionIdOrderByTotalScoreDesc(UUID sessionId);

    Optional<SessionPlayer> findBySessionIdAndUserId(UUID sessionId, UUID userId);

    Optional<SessionPlayer> findBySessionIdAndNickname(UUID sessionId, String nickname);

    boolean existsBySessionIdAndNickname(UUID sessionId, String nickname);
}
