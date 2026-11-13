package com.studexa.deck.dto;

import java.time.Instant;
import java.util.UUID;

import com.studexa.deck.Deck;

public record DeckResponse(
        UUID id,
        UUID subjectId,
        String subjectName,
        String title,
        String description,
        String color,
        String icon,
        long cardCount,
        long dueCount,
        Instant createdAt,
        Instant updatedAt) {

    public static DeckResponse from(Deck deck, String subjectName, long cardCount, long dueCount) {
        return new DeckResponse(
                deck.getId(),
                deck.getSubjectId(),
                subjectName,
                deck.getTitle(),
                deck.getDescription(),
                deck.getColor(),
                deck.getIcon(),
                cardCount,
                dueCount,
                deck.getCreatedAt(),
                deck.getUpdatedAt());
    }
}
