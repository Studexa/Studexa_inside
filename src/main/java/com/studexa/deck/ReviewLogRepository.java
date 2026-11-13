package com.studexa.deck;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, UUID> {

    Optional<ReviewLog> findByClientReviewId(UUID clientReviewId);

    List<ReviewLog> findTop1ByCardIdAndUserIdOrderByReviewedAtDesc(UUID cardId, UUID userId);

    List<ReviewLog> findByCardIdAndUserIdOrderByReviewedAtDesc(UUID cardId, UUID userId);
}
