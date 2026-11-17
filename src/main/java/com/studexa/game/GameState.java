package com.studexa.game;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One row per student. All game data (XP, level, streak, coins, badges)
 * changes only through {@link GameService}, inside a transaction that locks
 * this row.
 */
@Entity
@Table(name = "game_state")
public class GameState {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private long xpTotal;

    @Column(nullable = false)
    private int level;

    @Column(name = "xp_in_level", nullable = false)
    private long xpInLevel;

    @Column(nullable = false)
    private long coins;

    @Column(name = "streak_current", nullable = false)
    private int streakCurrent;

    @Column(name = "streak_best", nullable = false)
    private int streakBest;

    @Column(name = "streak_freezes", nullable = false)
    private int streakFreezes;

    @Column(name = "last_active_date", nullable = false)
    private LocalDate lastActiveDate;

    @Column(name = "daily_xp_today", nullable = false)
    private long dailyXpToday;

    @Column(name = "daily_xp_date", nullable = false)
    private LocalDate dailyXpDate;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public GameState() {
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public long getXpTotal() {
        return xpTotal;
    }

    public void setXpTotal(long xpTotal) {
        this.xpTotal = xpTotal;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public long getXpInLevel() {
        return xpInLevel;
    }

    public void setXpInLevel(long xpInLevel) {
        this.xpInLevel = xpInLevel;
    }

    public long getCoins() {
        return coins;
    }

    public void setCoins(long coins) {
        this.coins = coins;
    }

    public int getStreakCurrent() {
        return streakCurrent;
    }

    public void setStreakCurrent(int streakCurrent) {
        this.streakCurrent = streakCurrent;
    }

    public int getStreakBest() {
        return streakBest;
    }

    public void setStreakBest(int streakBest) {
        this.streakBest = streakBest;
    }

    public int getStreakFreezes() {
        return streakFreezes;
    }

    public void setStreakFreezes(int streakFreezes) {
        this.streakFreezes = streakFreezes;
    }

    public LocalDate getLastActiveDate() {
        return lastActiveDate;
    }

    public void setLastActiveDate(LocalDate lastActiveDate) {
        this.lastActiveDate = lastActiveDate;
    }

    public long getDailyXpToday() {
        return dailyXpToday;
    }

    public void setDailyXpToday(long dailyXpToday) {
        this.dailyXpToday = dailyXpToday;
    }

    public LocalDate getDailyXpDate() {
        return dailyXpDate;
    }

    public void setDailyXpDate(LocalDate dailyXpDate) {
        this.dailyXpDate = dailyXpDate;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public static GameState newFor(UUID userId, Instant now, LocalDate today) {
        GameState state = new GameState();
        state.userId = userId;
        state.xpTotal = 0;
        state.level = 1;
        state.xpInLevel = 0;
        state.coins = 0;
        state.streakCurrent = 0;
        state.streakBest = 0;
        state.streakFreezes = 0;
        state.lastActiveDate = today;
        state.dailyXpToday = 0;
        state.dailyXpDate = today;
        state.updatedAt = now;
        return state;
    }
}
