package com.studexa.srs;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Builds a study queue in the order learning cards, review cards, new cards.
 * Groups are shuffled so repeated sessions do not always show the same order.
 */
public final class StudyQueueBuilder {

    private final SchedulerConfig config;

    public StudyQueueBuilder() {
        this(SchedulerConfig.defaults());
    }

    public StudyQueueBuilder(SchedulerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public List<QueueCard> build(Collection<QueueCard> cards, Instant now) {
        return build(cards, QueueOptions.defaults(), now);
    }

    public List<QueueCard> build(Collection<QueueCard> cards, QueueOptions options, Instant now) {
        Objects.requireNonNull(cards, "cards");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(now, "now");

        Instant aheadCutoff = options.studyAhead()
                ? now.plus(config.studyAheadDays(), ChronoUnit.DAYS)
                : now;

        List<QueueCard> learning = new ArrayList<>();
        List<QueueCard> reviews = new ArrayList<>();
        List<QueueCard> newCards = new ArrayList<>();

        for (QueueCard card : cards) {
            if (card.suspended() || (options.starredOnly() && !card.starred())) {
                continue;
            }
            if (card.isLearning()) {
                if (card.state().isDue(now)) {
                    learning.add(card);
                }
            } else if (card.isReview()) {
                if (!card.state().dueAt().isAfter(aheadCutoff)) {
                    reviews.add(card);
                }
            } else if (card.isNew()) {
                newCards.add(card);
            }
        }

        Collections.shuffle(learning, options.random());
        Collections.shuffle(reviews, options.random());
        Collections.shuffle(newCards, options.random());

        List<QueueCard> result = new ArrayList<>();
        int reviewBudget = options.reviewLimit();
        reviewBudget = addWithinBudget(result, learning, reviewBudget);
        addWithinBudget(result, reviews, reviewBudget);

        int newBudget = options.newLimit();
        addWithinBudget(result, newCards, newBudget);

        return List.copyOf(result);
    }

    private static int addWithinBudget(List<QueueCard> target, List<QueueCard> source, int budget) {
        for (QueueCard card : source) {
            if (budget <= 0) {
                break;
            }
            target.add(card);
            budget--;
        }
        return budget;
    }
}
