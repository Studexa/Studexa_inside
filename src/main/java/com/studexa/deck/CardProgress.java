package com.studexa.deck;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Scheduling state for a card. Step 5 only creates NEW rows; the SRS engine
 * will own updates from Step 6 onward.
 */
@Entity
@Table(name = "card_progress")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardProgress {

    public static final String STATE_NEW = "NEW";

    @Id
    @Column(name = "card_id")
    private UUID cardId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private double ease;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(nullable = false)
    private int reps;

    @Column(nullable = false)
    private int lapses;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(nullable = false, length = 20)
    private String state;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static CardProgress newFor(UUID cardId, UUID userId, Instant now) {
        CardProgress progress = new CardProgress();
        progress.cardId = cardId;
        progress.userId = userId;
        progress.ease = 2.5;
        progress.intervalDays = 0;
        progress.reps = 0;
        progress.lapses = 0;
        progress.dueAt = now;
        progress.state = STATE_NEW;
        return progress;
    }

    public static CardProgress newFor(UUID cardId, UUID userId) {
        return newFor(cardId, userId, Instant.now());
    }

    public boolean isDue(Instant now) {
        return !dueAt.isAfter(now);
    }
}
