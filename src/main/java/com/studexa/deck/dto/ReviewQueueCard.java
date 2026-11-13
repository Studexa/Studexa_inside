package com.studexa.deck.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.studexa.deck.CardType;

public record ReviewQueueCard(
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
        String sourceExcerpt,
        String state,
        int intervalDays,
        int reps,
        int lapses,
        double ease,
        Instant dueAt,
        boolean due,
        Map<com.studexa.srs.Rating, String> previewLabels) {
}
