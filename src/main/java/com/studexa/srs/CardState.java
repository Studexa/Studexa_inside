package com.studexa.srs;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable scheduling state for one card. This record has no Spring, JPA, or
 * web dependencies so the scheduling rules can be tested in isolation.
 */
public record CardState(
        double ease,
        int intervalDays,
        int reps,
        int lapses,
        Instant dueAt,
        CardStateType state) {

    public CardState {
        Objects.requireNonNull(dueAt, "dueAt");
        Objects.requireNonNull(state, "state");
        if (ease <= 0) {
            throw new IllegalArgumentException("ease must be positive");
        }
        if (intervalDays < 0) {
            throw new IllegalArgumentException("intervalDays must not be negative");
        }
        if (reps < 0) {
            throw new IllegalArgumentException("reps must not be negative");
        }
        if (lapses < 0) {
            throw new IllegalArgumentException("lapses must not be negative");
        }
    }

    public boolean isDue(Instant now) {
        return !dueAt.isAfter(now);
    }
}
