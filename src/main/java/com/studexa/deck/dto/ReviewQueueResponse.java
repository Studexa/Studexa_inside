package com.studexa.deck.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.studexa.deck.CardType;

public record ReviewQueueResponse(
        List<ReviewQueueCard> cards,
        int remainingNew,
        int remainingReview,
        Instant now) {
}
