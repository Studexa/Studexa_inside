package com.studexa.boss;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A boss battle record (5-question exam-prep fight) owned by a student.
 * Tracks win/loss, questions attempted, and hearts remaining for XP
 * and badge tracking.
 */
@Entity
@Table(name = "boss_fights")
public class BossFight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Boolean won;

    @Column(name = "questions_count", nullable = false)
    private Integer questionsCount;

    @Column(name = "hearts_remaining", nullable = false)
    private Integer heartsRemaining;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public BossFight() {
    }

    private BossFight(UUID userId, Boolean won, Integer questionsCount, Integer heartsRemaining, Instant completedAt) {
        this.userId = userId;
        this.won = won;
        this.questionsCount = questionsCount;
        this.heartsRemaining = heartsRemaining;
        this.completedAt = completedAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static BossFight create(UUID userId, Boolean won, Integer questionsCount, Integer heartsRemaining, Instant completedAt) {
        return new BossFight(userId, won, questionsCount, heartsRemaining, completedAt);
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Boolean getWon() {
        return won;
    }

    public void setWon(Boolean won) {
        this.won = won;
    }

    public Integer getQuestionsCount() {
        return questionsCount;
    }

    public void setQuestionsCount(Integer questionsCount) {
        this.questionsCount = questionsCount;
    }

    public Integer getHeartsRemaining() {
        return heartsRemaining;
    }

    public void setHeartsRemaining(Integer heartsRemaining) {
        this.heartsRemaining = heartsRemaining;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}