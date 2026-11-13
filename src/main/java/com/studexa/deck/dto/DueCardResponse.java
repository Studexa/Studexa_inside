package com.studexa.deck.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.studexa.deck.Card;
import com.studexa.deck.CardType;
import com.studexa.deck.Deck;

public record DueCardResponse(
        UUID id,
        UUID deckId,
        String deckTitle,
        String deckColor,
        String deckIcon,
        String deckSubject,
        CardType type,
        String front,
        String back,
        Map<String, Object> extra,
        List<String> tags,
        String sourceTitle,
        String sourceExcerpt) {

    public static DueCardResponse from(Card card, Deck deck, String subjectName) {
        return new DueCardResponse(
                card.getId(),
                deck.getId(),
                deck.getTitle(),
                deck.getColor(),
                deck.getIcon(),
                subjectName == null ? deck.getTitle() : subjectName,
                card.getType(),
                card.getFront(),
                card.getBack(),
                card.getExtra(),
                card.getTags(),
                card.getSourceTitle(),
                card.getSourceExcerpt());
    }
}
