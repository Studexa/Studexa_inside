package com.studexa.deck;

import java.time.Instant;
import java.util.UUID;

import com.studexa.srs.CardState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Audit trail of every card review. Used for undo and history.
 */
@Entity
@Table(name = "review_logs")
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

    public ReviewLog() {
    }

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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getClientReviewId() {
        return clientReviewId;
    }

    public void setClientReviewId(UUID clientReviewId) {
        this.clientReviewId = clientReviewId;
    }

    public UUID getCardId() {
        return cardId;
    }

    public void setCardId(UUID cardId) {
        this.cardId = cardId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Integer getMsTaken() {
        return msTaken;
    }

    public void setMsTaken(Integer msTaken) {
        this.msTaken = msTaken;
    }

    public int getXpEarned() {
        return xpEarned;
    }

    public void setXpEarned(int xpEarned) {
        this.xpEarned = xpEarned;
    }

    public Double getPreviousEase() {
        return previousEase;
    }

    public void setPreviousEase(Double previousEase) {
        this.previousEase = previousEase;
    }

    public Integer getPreviousIntervalDays() {
        return previousIntervalDays;
    }

    public void setPreviousIntervalDays(Integer previousIntervalDays) {
        this.previousIntervalDays = previousIntervalDays;
    }

    public Integer getPreviousReps() {
        return previousReps;
    }

    public void setPreviousReps(Integer previousReps) {
        this.previousReps = previousReps;
    }

    public Integer getPreviousLapses() {
        return previousLapses;
    }

    public void setPreviousLapses(Integer previousLapses) {
        this.previousLapses = previousLapses;
    }

    public Instant getPreviousDueAt() {
        return previousDueAt;
    }

    public void setPreviousDueAt(Instant previousDueAt) {
        this.previousDueAt = previousDueAt;
    }

    public String getPreviousState() {
        return previousState;
    }

    public void setPreviousState(String previousState) {
        this.previousState = previousState;
    }
}
