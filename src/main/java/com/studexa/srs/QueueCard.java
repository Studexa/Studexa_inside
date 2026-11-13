package com.studexa.srs;

import java.util.Objects;
import java.util.UUID;

/**
 * A card as seen by the queue builder. The id is only a key; the builder does
 * not read cards or persist anything.
 */
public record QueueCard(
        UUID cardId,
        CardState state,
        boolean suspended,
        boolean starred) {

    public QueueCard {
        Objects.requireNonNull(cardId, "cardId");
        Objects.requireNonNull(state, "state");
    }

    public boolean isNew() {
        return state.state() == CardStateType.NEW;
    }

    public boolean isLearning() {
        return state.state() == CardStateType.LEARNING;
    }

    public boolean isReview() {
        return state.state() == CardStateType.REVIEW;
    }
}
