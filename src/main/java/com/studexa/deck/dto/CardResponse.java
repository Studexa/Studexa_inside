package com.studexa.deck.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.studexa.deck.Card;
import com.studexa.deck.CardType;

public record CardResponse(
        UUID id,
        UUID deckId,
        CardType type,
        String front,
        String back,
        Map<String, Object> extra,
        List<String> tags,
        String mediaUrl,
        boolean starred,
        boolean suspended,
        String sourceTitle,
        String sourceExcerpt,
        boolean due,
        Instant createdAt,
        Instant updatedAt) {

    public static CardResponse from(Card card, boolean due) {
        return new CardResponse(
                card.getId(),
                card.getDeckId(),
                card.getType(),
                card.getFront(),
                card.getBack(),
                card.getExtra(),
                card.getTags(),
                card.getMediaUrl(),
                card.isStarred(),
                card.isSuspended(),
                card.getSourceTitle(),
                card.getSourceExcerpt(),
                due,
                card.getCreatedAt(),
                card.getUpdatedAt());
    }
}
