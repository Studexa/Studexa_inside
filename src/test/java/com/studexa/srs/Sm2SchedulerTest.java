package com.studexa.srs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

class Sm2SchedulerTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final Sm2Scheduler scheduler = new Sm2Scheduler(
            SchedulerConfig.defaults().withJitter(0), new Random(7));

    @Test
    void newCardHasExpectedDefaults() {
        CardState state = scheduler.newCard(NOW);

        assertThat(state.ease()).isEqualTo(2.5);
        assertThat(state.intervalDays()).isZero();
        assertThat(state.reps()).isZero();
        assertThat(state.lapses()).isZero();
        assertThat(state.dueAt()).isEqualTo(NOW);
        assertThat(state.state()).isEqualTo(CardStateType.NEW);
    }

    @Test
    void newCardRatedGoodIsScheduledOneDayOut() {
        CardState next = scheduler.schedule(scheduler.newCard(NOW), Rating.GOOD, NOW);

        assertThat(next.intervalDays()).isEqualTo(1);
        assertThat(next.reps()).isEqualTo(1);
        assertThat(next.ease()).isEqualTo(2.5);
        assertThat(next.state()).isEqualTo(CardStateType.REVIEW);
        assertThat(next.dueAt()).isEqualTo(NOW.plus(1, java.time.temporal.ChronoUnit.DAYS));
    }

    @Test
    void secondGoodIsScheduledThreeDaysOut() {
        CardState first = scheduler.schedule(scheduler.newCard(NOW), Rating.GOOD, NOW);
        CardState second = scheduler.schedule(first, Rating.GOOD, NOW);

        assertThat(second.intervalDays()).isEqualTo(3);
        assertThat(second.reps()).isEqualTo(2);
        assertThat(second.ease()).isEqualTo(2.5);
    }

    @Test
    void laterGoodMultipliesIntervalByEase() {
        CardState state = scheduler.newCard(NOW);
        state = scheduler.schedule(state, Rating.GOOD, NOW);
        state = scheduler.schedule(state, Rating.GOOD, NOW);

        CardState third = scheduler.schedule(state, Rating.GOOD, NOW);

        assertThat(third.intervalDays()).isEqualTo(8);
        assertThat(third.reps()).isEqualTo(3);
    }

    @Test
    void againResetsRepsAndSchedulesTenMinutesOut() {
        CardState learned = scheduler.schedule(scheduler.newCard(NOW), Rating.GOOD, NOW);

        CardState again = scheduler.schedule(learned, Rating.AGAIN, NOW);

        assertThat(again.intervalDays()).isZero();
        assertThat(again.reps()).isZero();
        assertThat(again.lapses()).isEqualTo(1);
        assertThat(again.state()).isEqualTo(CardStateType.LEARNING);
        assertThat(again.dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void againLowersEaseByPointTwo() {
        CardState again = scheduler.schedule(scheduler.newCard(NOW), Rating.AGAIN, NOW);

        assertThat(again.ease()).isEqualTo(2.3);
    }

    @Test
    void easeNeverDropsBelowTheMinimum() {
        CardState state = scheduler.newCard(NOW);

        for (int i = 0; i < 20; i++) {
            state = scheduler.schedule(state, Rating.AGAIN, NOW);
        }

        assertThat(state.ease()).isEqualTo(1.3);
        assertThat(state.lapses()).isEqualTo(20);
    }

    @Test
    void hardOnNewCardSchedulesOneDay() {
        CardState hard = scheduler.schedule(scheduler.newCard(NOW), Rating.HARD, NOW);

        assertThat(hard.intervalDays()).isEqualTo(1);
        assertThat(hard.ease()).isEqualTo(2.35);
        assertThat(hard.reps()).isEqualTo(1);
        assertThat(hard.state()).isEqualTo(CardStateType.REVIEW);
    }

    @Test
    void hardExtendsExistingIntervalByTwentyPercent() {
        CardState review = new CardState(2.5, 10, 3, 0, NOW, CardStateType.REVIEW);

        CardState hard = scheduler.schedule(review, Rating.HARD, NOW);

        assertThat(hard.intervalDays()).isEqualTo(12);
        assertThat(hard.ease()).isEqualTo(2.35);
        assertThat(hard.reps()).isEqualTo(4);
    }

    @Test
    void easyOnNewCardSchedulesFourDays() {
        CardState easy = scheduler.schedule(scheduler.newCard(NOW), Rating.EASY, NOW);

        assertThat(easy.intervalDays()).isEqualTo(4);
        assertThat(easy.ease()).isEqualTo(2.65);
        assertThat(easy.reps()).isEqualTo(1);
        assertThat(easy.state()).isEqualTo(CardStateType.REVIEW);
    }

    @Test
    void easyGrowsIntervalAndEase() {
        CardState first = scheduler.schedule(scheduler.newCard(NOW), Rating.EASY, NOW);
        CardState second = scheduler.schedule(first, Rating.EASY, NOW);

        assertThat(second.intervalDays()).isEqualTo(15);
        assertThat(second.ease()).isEqualTo(2.8);
        assertThat(second.reps()).isEqualTo(2);
    }

    @Test
    void previewLabelsForNewCardMatchTheRules() {
        Map<Rating, String> labels = scheduler.previewLabels(scheduler.newCard(NOW));

        assertThat(labels).containsExactly(
                Map.entry(Rating.AGAIN, "<10m"),
                Map.entry(Rating.HARD, "1d"),
                Map.entry(Rating.GOOD, "1d"),
                Map.entry(Rating.EASY, "4d"));
    }

    @Test
    void previewLabelsAfterFirstReviewMatchTheRules() {
        CardState first = scheduler.schedule(scheduler.newCard(NOW), Rating.GOOD, NOW);

        Map<Rating, String> labels = scheduler.previewLabels(first);

        assertThat(labels).containsExactly(
                Map.entry(Rating.AGAIN, "<10m"),
                Map.entry(Rating.HARD, "1d"),
                Map.entry(Rating.GOOD, "3d"),
                Map.entry(Rating.EASY, "3d"));
    }

    @Test
    void previewLabelsDoNotConsumeRandomness() {
        Sm2Scheduler jittered = new Sm2Scheduler(
                SchedulerConfig.defaults(), fixedRandom(0.0));
        CardState state = new CardState(2.5, 20, 4, 0, NOW, CardStateType.REVIEW);

        assertThat(jittered.previewLabels(state)).isEqualTo(jittered.previewLabels(state));
    }

    @Test
    void jitterIsNotAppliedToShortIntervals() {
        Sm2Scheduler jittered = new Sm2Scheduler(
                SchedulerConfig.defaults(), fixedRandom(0.0));

        CardState first = jittered.schedule(jittered.newCard(NOW), Rating.GOOD, NOW);
        CardState second = jittered.schedule(first, Rating.GOOD, NOW);

        assertThat(first.intervalDays()).isEqualTo(1);
        assertThat(second.intervalDays()).isEqualTo(3);
    }

    @Test
    void jitterMovesLongIntervalsWithinFivePercent() {
        Sm2Scheduler shortJitter = new Sm2Scheduler(
                SchedulerConfig.defaults(), fixedRandom(0.0));
        Sm2Scheduler longJitter = new Sm2Scheduler(
                SchedulerConfig.defaults(), fixedRandom(0.999999));

        CardState easy = shortJitter.schedule(shortJitter.newCard(NOW), Rating.EASY, NOW);
        CardState lower = shortJitter.schedule(easy, Rating.EASY, NOW);
        CardState upper = longJitter.schedule(
                longJitter.schedule(longJitter.newCard(NOW), Rating.EASY, NOW),
                Rating.EASY,
                NOW);

        assertThat(lower.intervalDays()).isEqualTo(14);
        assertThat(upper.intervalDays()).isEqualTo(16);
    }

    @Test
    void tenExampleHistoriesMatchTheRules() {
        List<History> histories = List.of(
                new History("Good", List.of(Rating.GOOD), 1, 2.5, 1, CardStateType.REVIEW),
                new History("Good x2", List.of(Rating.GOOD, Rating.GOOD), 3, 2.5, 2, CardStateType.REVIEW),
                new History("Good x3", List.of(Rating.GOOD, Rating.GOOD, Rating.GOOD), 8, 2.5, 3,
                        CardStateType.REVIEW),
                new History("Again", List.of(Rating.AGAIN), 0, 2.3, 0, CardStateType.LEARNING),
                new History("Good then Again", List.of(Rating.GOOD, Rating.AGAIN), 0, 2.3, 0,
                        CardStateType.LEARNING),
                new History("Easy", List.of(Rating.EASY), 4, 2.65, 1, CardStateType.REVIEW),
                new History("Easy x2", List.of(Rating.EASY, Rating.EASY), 15, 2.8, 2,
                        CardStateType.REVIEW),
                new History("Hard", List.of(Rating.HARD), 1, 2.35, 1, CardStateType.REVIEW),
                new History("Hard then Good", List.of(Rating.HARD, Rating.GOOD), 3, 2.35, 2,
                        CardStateType.REVIEW),
                new History("Good then Easy", List.of(Rating.GOOD, Rating.EASY), 3, 2.65, 2,
                        CardStateType.REVIEW));

        System.out.println("SRS example histories (ease 2.5 start, jitter off)");
        System.out.printf("%-16s %-10s %-8s %-6s %-6s %-8s%n",
                "history", "state", "interval", "ease", "reps", "lapses");

        for (History history : histories) {
            CardState state = scheduler.newCard(NOW);
            for (Rating rating : history.ratings()) {
                state = scheduler.schedule(state, rating, NOW);
            }
            System.out.printf("%-16s %-10s %-8d %-6.2f %-6d %-8d%n",
                    history.name(),
                    state.state(),
                    state.intervalDays(),
                    state.ease(),
                    state.reps(),
                    state.lapses());

            assertThat(state.intervalDays()).as(history.name()).isEqualTo(history.intervalDays());
            assertThat(state.ease()).as(history.name()).isEqualTo(history.ease());
            assertThat(state.reps()).as(history.name()).isEqualTo(history.reps());
            assertThat(state.state()).as(history.name()).isEqualTo(history.state());
        }
    }

    private static Random fixedRandom(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    private record History(
            String name,
            List<Rating> ratings,
            int intervalDays,
            double ease,
            int reps,
            CardStateType state) {
    }
}
