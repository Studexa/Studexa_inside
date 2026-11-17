package com.studexa.deck;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "card_progress")
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

    public CardProgress() {
    }

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

    public double getEase() {
        return ease;
    }

    public void setEase(double ease) {
        this.ease = ease;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(int intervalDays) {
        this.intervalDays = intervalDays;
    }

    public int getReps() {
        return reps;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    public int getLapses() {
        return lapses;
    }

    public void setLapses(int lapses) {
        this.lapses = lapses;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Instant getLastReviewedAt() {
        return lastReviewedAt;
    }

    public void setLastReviewedAt(Instant lastReviewedAt) {
        this.lastReviewedAt = lastReviewedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
