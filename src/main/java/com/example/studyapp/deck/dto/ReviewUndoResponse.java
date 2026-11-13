package com.example.studyapp.deck.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.studyapp.deck.CardType;

public record ReviewUndoResponse(
        UUID cardId,
        UUID deckId,
        CardType type,
        String front,
        String back,
        Map<String, Object> extra,
        List<String> tags,
        String state,
        int intervalDays,
        int reps,
        int lapses,
        double ease,
        Instant dueAt,
        Map<com.example.studyapp.srs.Rating, String> previewLabels,
        Instant now) {
}
