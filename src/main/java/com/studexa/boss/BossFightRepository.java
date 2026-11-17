package com.studexa.boss;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BossFightRepository extends JpaRepository<BossFight, UUID> {

    List<BossFight> findByUserIdOrderByCompletedAtDesc(UUID userId);

    @Query("SELECT b FROM BossFight b WHERE b.userId = :userId AND b.completedAt >= :start AND b.completedAt < :end")
    List<BossFight> findByUserIdAndCompletedAtBetween(
            @Param("userId") UUID userId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    Long countByUserIdAndWonTrue(UUID userId);

    Long countByUserIdAndCompletedAtBetween(UUID userId, Instant start, Instant end);
}