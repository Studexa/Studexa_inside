package com.studexa.deck;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeckRepository extends JpaRepository<Deck, UUID> {

    List<Deck> findTop100ByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<Deck> findByIdAndUserId(UUID id, UUID userId);

    List<Deck> findAllByIdInAndUserId(List<UUID> ids, UUID userId);
}
