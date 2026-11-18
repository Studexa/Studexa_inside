package com.studexa.focus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FocusSessionRepository extends JpaRepository<FocusSession, UUID> {

    long countByUserId(UUID userId);

    List<FocusSession> findByUserIdOrderByCompletedAtDesc(UUID userId);

    @Query("SELECT f FROM FocusSession f WHERE f.userId = :userId AND f.completedAt >= :start AND f.completedAt < :end")
    List<FocusSession> findByUserIdAndCompletedAtBetween(
            @Param("userId") UUID userId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    Long countByUserIdAndCompletedAtBetween(UUID userId, Instant start, Instant end);
}
