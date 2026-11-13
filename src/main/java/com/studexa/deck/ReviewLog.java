package com.studexa.deck;

import java.time.Instant;
import java.util.UUID;

import com.studexa.srs.CardState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Audit trail of every card review. Used for undo and history.
 */
@Entity
@Table(name = "review_logs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewLog {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "client_review_id", nullable = false, unique = true)
    private UUID clientReviewId;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String rating;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Column(name = "ms_taken")
    private Integer msTaken;

    @Column(name = "xp_earned", nullable = false)
    private int xpEarned;

    @Column(name = "previous_ease")
    private Double previousEase;

    @Column(name = "previous_interval_days")
    private Integer previousIntervalDays;

    @Column(name = "previous_reps")
    private Integer previousReps;

    @Column(name = "previous_lapses")
    private Integer previousLapses;

    @Column(name = "previous_due_at")
    private Instant previousDueAt;

    @Column(name = "previous_state", length = 20)
    private String previousState;

    public static ReviewLog create(UUID id, UUID clientReviewId, UUID cardId, UUID userId,
            String rating, Instant reviewedAt, Integer msTaken, int xpEarned,
            CardProgress previous, CardState previousState) {
        ReviewLog log = new ReviewLog();
        log.id = id;
        log.clientReviewId = clientReviewId;
        log.cardId = cardId;
        log.userId = userId;
        log.rating = rating;
        log.reviewedAt = reviewedAt;
        log.msTaken = msTaken;
        log.xpEarned = xpEarned;
        log.previousEase = previous.getEase();
        log.previousIntervalDays = previous.getIntervalDays();
        log.previousReps = previous.getReps();
        log.previousLapses = previous.getLapses();
        log.previousDueAt = previous.getDueAt();
        log.previousState = previous.getState();
        return log;
    }
}
