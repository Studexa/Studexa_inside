package com.studexa.srs;

import java.util.Objects;
import java.util.Random;

/**
 * Limits for one study session. Callers pass the limits that remain for the
 * day, so the builder itself does not need a clock or database.
 */
public record QueueOptions(
        int newLimit,
        int reviewLimit,
        boolean studyAhead,
        boolean starredOnly,
        Random random) {

    public QueueOptions {
        Objects.requireNonNull(random, "random");
        if (newLimit < 0) {
            throw new IllegalArgumentException("newLimit must not be negative");
        }
        if (reviewLimit < 0) {
            throw new IllegalArgumentException("reviewLimit must not be negative");
        }
    }

    public static QueueOptions defaults() {
        return defaults(SchedulerConfig.defaults(), new Random());
    }

    public static QueueOptions defaults(Random random) {
        return defaults(SchedulerConfig.defaults(), random);
    }

    public static QueueOptions defaults(SchedulerConfig config, Random random) {
        return new QueueOptions(
                config.newCardsPerDay(),
                config.reviewsPerDay(),
                false,
                false,
                random);
    }
}
