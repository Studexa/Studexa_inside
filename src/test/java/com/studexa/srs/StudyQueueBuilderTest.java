package com.studexa.srs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class StudyQueueBuilderTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final StudyQueueBuilder builder = new StudyQueueBuilder(
            SchedulerConfig.defaults());

    @Test
    void cardDueTodayAppearsButTomorrowsReviewDoesNot() {
        QueueCard dueNow = reviewCard(1, NOW);
        QueueCard dueTomorrow = reviewCard(2, NOW.plus(1, ChronoUnit.DAYS));

        List<QueueCard> queue = builder.build(List.of(dueNow, dueTomorrow),
                options(20, 100, false, false), NOW);

        assertThat(queue).extracting(QueueCard::cardId)
                .containsExactly(dueNow.cardId());
    }

    @Test
    void studyAheadIncludesReviewsDueInTheNextThreeDays() {
        QueueCard dueTomorrow = reviewCard(1, NOW.plus(1, ChronoUnit.DAYS));
        QueueCard dueInThreeDays = reviewCard(2, NOW.plus(3, ChronoUnit.DAYS));
        QueueCard dueInFourDays = reviewCard(3, NOW.plus(4, ChronoUnit.DAYS));

        List<QueueCard> queue = builder.build(
                List.of(dueTomorrow, dueInThreeDays, dueInFourDays),
                options(20, 100, true, false),
                NOW);

        assertThat(queue).extracting(QueueCard::cardId)
                .containsExactlyInAnyOrder(dueTomorrow.cardId(), dueInThreeDays.cardId());
    }

    @Test
    void newCardLimitIsRespected() {
        List<QueueCard> cards = manyCards(25, CardStateType.NEW, NOW);

        List<QueueCard> queue = builder.build(cards, options(20, 100, false, false), NOW);

        assertThat(queue).hasSize(20).allMatch(QueueCard::isNew);
    }

    @Test
    void reviewLimitIsRespected() {
        List<QueueCard> cards = manyCards(120, CardStateType.REVIEW, NOW);

        List<QueueCard> queue = builder.build(cards, options(20, 100, false, false), NOW);

        assertThat(queue).hasSize(100).allMatch(QueueCard::isReview);
    }

    @Test
    void learningAndReviewCardsShareTheReviewBudget() {
        List<QueueCard> cards = new ArrayList<>(manyCards(120, CardStateType.REVIEW, NOW));
        cards.add(learningCard(200, NOW));
        cards.add(learningCard(201, NOW));

        List<QueueCard> queue = builder.build(cards, options(20, 100, false, false), NOW);

        assertThat(queue).hasSize(100);
        assertThat(queue.subList(0, 2)).allMatch(QueueCard::isLearning);
    }

    @Test
    void queueOrdersLearningThenReviewThenNew() {
        QueueCard learning = learningCard(1, NOW);
        QueueCard review = reviewCard(2, NOW);
        QueueCard fresh = newCard(3);

        List<QueueCard> queue = builder.build(
                List.of(fresh, review, learning),
                options(20, 100, false, false),
                NOW);

        assertThat(queue).extracting(QueueCard::state)
                .extracting(CardState::state)
                .containsExactly(
                        CardStateType.LEARNING,
                        CardStateType.REVIEW,
                        CardStateType.NEW);
    }

    @Test
    void suspendedCardsNeverAppear() {
        QueueCard suspendedReview = new QueueCard(
                UUID.randomUUID(), reviewState(NOW), true, false);
        QueueCard suspendedNew = new QueueCard(
                UUID.randomUUID(), newState(), true, false);

        List<QueueCard> queue = builder.build(
                List.of(suspendedReview, suspendedNew),
                options(20, 100, false, false),
                NOW);

        assertThat(queue).isEmpty();
    }

    @Test
    void starredOnlyKeepsStarredCards() {
        QueueCard starred = new QueueCard(
                UUID.randomUUID(), reviewState(NOW), false, true);
        QueueCard plain = new QueueCard(
                UUID.randomUUID(), reviewState(NOW), false, false);

        List<QueueCard> queue = builder.build(
                List.of(starred, plain), options(20, 100, false, true), NOW);

        assertThat(queue).extracting(QueueCard::cardId)
                .containsExactly(starred.cardId());
    }

    @Test
    void learningCardThatIsNotDueYetIsSkipped() {
        QueueCard later = learningCard(1, NOW.plus(10, ChronoUnit.MINUTES));

        List<QueueCard> queue = builder.build(
                List.of(later), options(20, 100, false, false), NOW);

        assertThat(queue).isEmpty();
    }

    @Test
    void overdueReviewsAreIncluded() {
        QueueCard overdue = reviewCard(1, NOW.minus(2, ChronoUnit.DAYS));

        List<QueueCard> queue = builder.build(
                List.of(overdue), options(20, 100, false, false), NOW);

        assertThat(queue).containsExactly(overdue);
    }

    @Test
    void zeroLimitsProduceAnEmptyQueue() {
        List<QueueCard> cards = new ArrayList<>(manyCards(5, CardStateType.NEW, NOW));
        cards.add(reviewCard(50, NOW));

        List<QueueCard> queue = builder.build(cards, options(0, 0, false, false), NOW);

        assertThat(queue).isEmpty();
    }

    private static QueueOptions options(int newLimit, int reviewLimit, boolean studyAhead,
            boolean starredOnly) {
        return new QueueOptions(newLimit, reviewLimit, studyAhead, starredOnly, new Random(1));
    }

    private static List<QueueCard> manyCards(int count, CardStateType type, Instant dueAt) {
        List<QueueCard> cards = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            cards.add(new QueueCard(
                    uuid(i),
                    type == CardStateType.NEW ? newState() : reviewState(dueAt),
                    false,
                    false));
        }
        return cards;
    }

    private static QueueCard newCard(int n) {
        return new QueueCard(uuid(n), newState(), false, false);
    }

    private static QueueCard learningCard(int n, Instant dueAt) {
        return new QueueCard(uuid(n), learningState(dueAt), false, false);
    }

    private static QueueCard reviewCard(int n, Instant dueAt) {
        return new QueueCard(uuid(n), reviewState(dueAt), false, false);
    }

    private static UUID uuid(int n) {
        return new UUID(0, n);
    }

    private static CardState newState() {
        return new CardState(2.5, 0, 0, 0, NOW, CardStateType.NEW);
    }

    private static CardState learningState(Instant dueAt) {
        return new CardState(2.3, 0, 0, 1, dueAt, CardStateType.LEARNING);
    }

    private static CardState reviewState(Instant dueAt) {
        return new CardState(2.5, 3, 2, 0, dueAt, CardStateType.REVIEW);
    }
}
