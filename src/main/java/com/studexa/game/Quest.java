package com.studexa.game;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A daily or weekly quest for a student. Created lazily on the first request
 * of the student's local day (Section 7.7).
 */
@Entity
@Table(name = "quests")
public class Quest {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestKind kind;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false)
    private int target;

    @Column(nullable = false)
    private int progress;

    @Column(name = "reward_xp", nullable = false)
    private int rewardXp;

    @Column(name = "reward_coins", nullable = false)
    private int rewardCoins;

    @Column(name = "quest_date", nullable = false)
    private LocalDate questDate;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Quest() {
    }

    public Quest(UUID id, UUID userId, QuestKind kind, String code, int target,
            int rewardXp, int rewardCoins, LocalDate questDate, Instant now) {
        this.id = id;
        this.userId = userId;
        this.kind = kind;
        this.code = code;
        this.target = target;
        this.progress = 0;
        this.rewardXp = rewardXp;
        this.rewardCoins = rewardCoins;
        this.questDate = questDate;
        this.completed = false;
        this.createdAt = now;
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

    public QuestKind getKind() {
        return kind;
    }

    public void setKind(QuestKind kind) {
        this.kind = kind;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getRewardXp() {
        return rewardXp;
    }

    public void setRewardXp(int rewardXp) {
        this.rewardXp = rewardXp;
    }

    public int getRewardCoins() {
        return rewardCoins;
    }

    public void setRewardCoins(int rewardCoins) {
        this.rewardCoins = rewardCoins;
    }

    public LocalDate getQuestDate() {
        return questDate;
    }

    public void setQuestDate(LocalDate questDate) {
        this.questDate = questDate;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
