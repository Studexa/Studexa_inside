package com.example.studyapp.deck.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.studyapp.deck.CardType;

public record ReviewQueueResponse(
        List<ReviewQueueCard> cards,
        int remainingNew,
        int remainingReview,
        Instant now) {
}
