package com.studexa.deck;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findTop100ByDeckIdAndUserIdOrderByCreatedAtAsc(UUID deckId, UUID userId);

    long countByDeckIdAndUserId(UUID deckId, UUID userId);

    long countByUserId(UUID userId);

    Optional<Card> findByIdAndUserId(UUID id, UUID userId);

    List<Card> findTop80ByDeckIdAndUserIdOrderByCreatedAtAsc(UUID deckId, UUID userId);

    List<Card> findTop80ByUserIdOrderByCreatedAtDesc(UUID userId);
}
