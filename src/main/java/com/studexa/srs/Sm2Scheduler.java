package com.studexa.srs;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Anki-style SM-2 scheduler. Pure Java: no Spring, JPA, or web imports.
 */
public final class Sm2Scheduler implements Scheduler {

    public static final int MAX_INTERVAL_DAYS = 3650;

    private final SchedulerConfig config;
    private final Random random;

    public Sm2Scheduler() {
        this(SchedulerConfig.defaults(), new Random());
    }

    public Sm2Scheduler(SchedulerConfig config, Random random) {
        this.config = Objects.requireNonNull(config, "config");
        this.random = Objects.requireNonNull(random, "random");
    }

    public CardState newCard(Instant now) {
        return new CardState(
                config.initialEase(),
                0,
                0,
                0,
                now,
                CardStateType.NEW);
    }

    @Override
    public CardState schedule(CardState current, Rating rating, Instant now) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(rating, "rating");
        Objects.requireNonNull(now, "now");

        return switch (rating) {
            case AGAIN -> scheduleAgain(current, now);
            case HARD -> scheduleHard(current, now);
            case GOOD -> scheduleGood(current, now);
            case EASY -> scheduleEasy(current, now);
        };
    }

    @Override
    public Map<Rating, String> previewLabels(CardState current) {
        Objects.requireNonNull(current, "current");

        Map<Rating, String> labels = new EnumMap<>(Rating.class);
        labels.put(Rating.AGAIN, "<" + config.againDelay().toMinutes() + "m");
        labels.put(Rating.HARD, daysLabel(previewInterval(current, Rating.HARD)));
        labels.put(Rating.GOOD, daysLabel(previewInterval(current, Rating.GOOD)));
        labels.put(Rating.EASY, daysLabel(previewInterval(current, Rating.EASY)));
        return Collections.unmodifiableMap(labels);
    }

    private CardState scheduleAgain(CardState current, Instant now) {
        double ease = Math.max(config.minimumEase(), current.ease() - config.againEasePenalty());
        ease = Math.min(config.maximumEase(), ease);
        return new CardState(
                roundEase(ease),
                0,
                0,
                current.lapses() + 1,
                now.plus(config.againDelay()),
                CardStateType.LEARNING);
    }

    private CardState scheduleHard(CardState current, Instant now) {
        double ease = Math.max(config.minimumEase(), current.ease() - config.hardEasePenalty());
        ease = Math.min(config.maximumEase(), ease);
        int interval = isNew(current)
                ? config.firstIntervalDays()
                : Math.max(1, (int) Math.round(current.intervalDays() * config.hardIntervalMultiplier()));
        return reviewState(current, now, roundEase(ease), interval);
    }

    private CardState scheduleGood(CardState current, Instant now) {
        int interval;
        if (current.reps() == 0) {
            interval = config.firstIntervalDays();
        } else if (current.reps() == 1) {
            interval = config.secondIntervalDays();
        } else {
            interval = Math.max(1, (int) Math.round(current.intervalDays() * current.ease()));
        }
        interval = applyJitter(interval);
        return reviewState(current, now, current.ease(), interval);
    }

    private CardState scheduleEasy(CardState current, Instant now) {
        double ease = Math.min(config.maximumEase(), current.ease() + config.easyEaseBonus());
        int interval = current.reps() == 0
                ? config.firstEasyIntervalDays()
                : Math.max(1, (int) Math.round(
                        current.intervalDays() * ease * config.easyIntervalMultiplier()));
        interval = applyJitter(interval);
        return reviewState(current, now, roundEase(ease), interval);
    }

    private CardState reviewState(CardState current, Instant now, double ease, int interval) {
        int clampedInterval = Math.min(MAX_INTERVAL_DAYS, Math.max(0, interval));
        double clampedEase = Math.min(config.maximumEase(), Math.max(config.minimumEase(), ease));
        return new CardState(
                roundEase(clampedEase),
                clampedInterval,
                current.reps() + 1,
                current.lapses(),
                now.plus(clampedInterval, ChronoUnit.DAYS),
                CardStateType.REVIEW);
    }

    private int previewInterval(CardState current, Rating rating) {
        int calculated = switch (rating) {
            case AGAIN -> 0;
            case HARD -> isNew(current)
                    ? config.firstIntervalDays()
                    : Math.max(1, (int) Math.round(current.intervalDays() * config.hardIntervalMultiplier()));
            case GOOD -> current.reps() == 0
                    ? config.firstIntervalDays()
                    : current.reps() == 1
                            ? config.secondIntervalDays()
                            : Math.max(1, (int) Math.round(current.intervalDays() * current.ease()));
            case EASY -> current.reps() == 0
                    ? config.firstEasyIntervalDays()
                    : Math.max(1, (int) Math.round(
                            current.intervalDays()
                                    * Math.min(config.maximumEase(), current.ease() + config.easyEaseBonus())
                                    * config.easyIntervalMultiplier()));
        };
        return Math.min(MAX_INTERVAL_DAYS, calculated);
    }

    private int applyJitter(int interval) {
        if (interval <= config.jitterThresholdDays() || config.jitterFraction() == 0) {
            return interval;
        }
        double factor = 1 + (random.nextDouble() * 2 - 1) * config.jitterFraction();
        return Math.max(1, (int) Math.round(interval * factor));
    }

    private static boolean isNew(CardState current) {
        return current.state() == CardStateType.NEW || current.intervalDays() == 0;
    }

    private static double roundEase(double ease) {
        return Math.round(ease * 100.0) / 100.0;
    }

    private static String daysLabel(int intervalDays) {
        return intervalDays + "d";
    }
}
