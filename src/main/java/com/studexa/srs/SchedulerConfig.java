package com.studexa.srs;

import java.time.Duration;
import java.util.Objects;

/**
 * Every tunable SRS constant lives here so the rules stay in one place.
 */
public record SchedulerConfig(
        double initialEase,
        double minimumEase,
        double maximumEase,
        double againEasePenalty,
        double hardEasePenalty,
        double easyEaseBonus,
        Duration againDelay,
        double hardIntervalMultiplier,
        double easyIntervalMultiplier,
        int firstIntervalDays,
        int secondIntervalDays,
        int firstEasyIntervalDays,
        int jitterThresholdDays,
        double jitterFraction,
        int newCardsPerDay,
        int reviewsPerDay,
        int studyAheadDays) {

    public SchedulerConfig {
        Objects.requireNonNull(againDelay, "againDelay");
        if (initialEase < minimumEase) {
            throw new IllegalArgumentException("initialEase must be at least minimumEase");
        }
        if (maximumEase < initialEase) {
            throw new IllegalArgumentException("maximumEase must be at least initialEase");
        }
        if (jitterFraction < 0 || jitterFraction > 0.5) {
            throw new IllegalArgumentException("jitterFraction must be between 0 and 0.5");
        }
        if (newCardsPerDay < 0 || reviewsPerDay < 0) {
            throw new IllegalArgumentException("daily limits must not be negative");
        }
        if (studyAheadDays < 0) {
            throw new IllegalArgumentException("studyAheadDays must not be negative");
        }
    }

    public static SchedulerConfig defaults() {
        return new SchedulerConfig(
                2.5,
                1.3,
                3.0,
                0.20,
                0.15,
                0.15,
                Duration.ofMinutes(10),
                1.2,
                1.3,
                1,
                3,
                4,
                7,
                0.05,
                20,
                100,
                3);
    }

    public SchedulerConfig withJitter(double fraction) {
        return new SchedulerConfig(
                initialEase,
                minimumEase,
                maximumEase,
                againEasePenalty,
                hardEasePenalty,
                easyEaseBonus,
                againDelay,
                hardIntervalMultiplier,
                easyIntervalMultiplier,
                firstIntervalDays,
                secondIntervalDays,
                firstEasyIntervalDays,
                jitterThresholdDays,
                fraction,
                newCardsPerDay,
                reviewsPerDay,
                studyAheadDays);
    }

    public SchedulerConfig withDailyLimits(int newCards, int reviews) {
        return new SchedulerConfig(
                initialEase,
                minimumEase,
                maximumEase,
                againEasePenalty,
                hardEasePenalty,
                easyEaseBonus,
                againDelay,
                hardIntervalMultiplier,
                easyIntervalMultiplier,
                firstIntervalDays,
                secondIntervalDays,
                firstEasyIntervalDays,
                jitterThresholdDays,
                jitterFraction,
                newCards,
                reviews,
                studyAheadDays);
    }
}
