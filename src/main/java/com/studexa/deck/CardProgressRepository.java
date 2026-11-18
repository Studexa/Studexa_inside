package com.studexa.deck;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardProgressRepository extends JpaRepository<CardProgress, UUID> {

    List<CardProgress> findAllByUserIdAndCardIdIn(UUID userId, Collection<UUID> cardIds);

    Optional<CardProgress> findByCardIdAndUserId(UUID cardId, UUID userId);

    long countByUserIdAndIntervalDaysGreaterThanEqual(UUID userId, int intervalDays);

    @Query(value = """
            SELECT count(*)
            FROM card_progress p
            JOIN cards c ON c.id = p.card_id
            WHERE p.user_id = :userId
              AND c.deck_id = :deckId
              AND c.suspended = false
              AND p.due_at <= :now
            """, nativeQuery = true)
    long countDueInDeck(@Param("userId") UUID userId, @Param("deckId") UUID deckId,
            @Param("now") Instant now);

    @Query(value = """
            SELECT p.card_id
            FROM card_progress p
            JOIN cards c ON c.id = p.card_id
            WHERE p.user_id = :userId
              AND c.suspended = false
              AND p.due_at <= :now
            ORDER BY p.due_at ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findDueCardIds(@Param("userId") UUID userId, @Param("now") Instant now,
            @Param("limit") int limit);
}
