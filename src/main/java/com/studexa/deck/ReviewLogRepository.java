package com.studexa.deck;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, UUID> {

    Optional<ReviewLog> findByClientReviewId(UUID clientReviewId);

    List<ReviewLog> findTop1ByCardIdAndUserIdOrderByReviewedAtDesc(UUID cardId, UUID userId);

    List<ReviewLog> findByCardIdAndUserIdOrderByReviewedAtDesc(UUID cardId, UUID userId);

    boolean existsByUserIdAndReviewedAtGreaterThanEqualAndReviewedAtLessThan(
            UUID userId, Instant startInclusive, Instant endExclusive);
    long countByUserIdAndReviewedAtGreaterThanEqualAndReviewedAtLessThan(
            UUID userId, Instant startInclusive, Instant endExclusive);

    @Query(value = "SELECT count(DISTINCT (reviewed_at AT TIME ZONE :zoneId)::date) "
            + "FROM review_logs WHERE user_id = :userId "
            + "AND EXTRACT(HOUR FROM reviewed_at AT TIME ZONE :zoneId) >= 22", nativeQuery = true)
    long countLateNightReviewDays(@Param("userId") UUID userId, @Param("zoneId") String zoneId);

    @Query(value = "SELECT count(DISTINCT (reviewed_at AT TIME ZONE :zoneId)::date) "
            + "FROM review_logs WHERE user_id = :userId "
            + "AND EXTRACT(HOUR FROM reviewed_at AT TIME ZONE :zoneId) < 6", nativeQuery = true)
    long countEarlyReviewDays(@Param("userId") UUID userId, @Param("zoneId") String zoneId);
}
