package com.studexa.game;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All game numbers live here (Section 7 of the build guide). Keeping them in
 * one {@link ConfigurationProperties} class makes them easy to tune without
 * touching code.
 */
@ConfigurationProperties(prefix = "app.game")
public record GameProperties(

        // --- XP (Section 7.1) ---
        int xpPerReview,
        int xpBonusGoodEasy,
        int xpFinishSession,
        int xpFinishSessionMinCards,
        int maxSessionsPerDay,
        int xpFocusSession,
        int xpFocusSessionMinMinutes,
        int maxFocusSessionsPerDay,
        int xpCreateCard,
        int xpCreateNote,
        int maxCreateXpPerDay,
        int xpDailyGoal,
        int xpBossWin,
        int xpQuizBase,
        int xpQuizPerCorrect,
        int maxQuizzesPerDay,
        double streakBonusPerSevenDays,
        double maxStreakBonus,
        int dailyXpCap,

        // --- Levels (Section 7.2) ---
        int levelXpBase,
        int levelXpIncrement,
        int levelUpCoins,

        // --- Streaks (Section 7.3) ---
        int streakFreezeCost,
        int maxFreezesHeld,

        // --- Coins (Section 7.4) ---
        int coinsPerTenXp,
        int coinsPerLevelUp,

        // --- Badges (Section 7.5) ---
        List<Map<String, Object>> badgeSeed,

        // --- Anti-cheat (Section 7.11) ---
        int tooFastThresholdMs,
        int tooFastConsecutiveCount,
        int offlineMaxDaysAgo,
        int maxReviewsPerCardPerDay) {

    public static GameProperties defaults() {
        return new GameProperties(
                3, 2, 20, 5, 5,
                25, 25, 8,
                2, 5, 20,
                30, 100, 10, 1, 5,
                0.10, 0.50, 600,
                100, 50, 20,
                50, 2,
                1, 10,
                List.of(),
                800, 10, 7, 1);
    }

    /**
     * XP needed to go from level L to L+1: 100 + (L - 1) * 50.
     */
    public int xpToNextLevel(int level) {
        return levelXpBase + (level - 1) * levelXpIncrement;
    }
}
