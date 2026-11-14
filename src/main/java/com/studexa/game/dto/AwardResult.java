package com.studexa.game.dto;

import java.util.List;

public record AwardResult(
        boolean awarded,
        int xpAwarded,
        int bonusXp,
        int newLevel,
        long xpInLevel,
        long newCoins,
        int coinsEarned,
        boolean leveledUp,
        int streakCurrent,
        int streakBest,
        int streakFreezes,
        long dailyXpToday,
        boolean dailyCapReached,
        List<BadgeDto> newBadges) {

    public static AwardResult none() {
        return new AwardResult(false, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, false, List.of());
    }

    public static AwardResult capped() {
        return new AwardResult(false, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, true, List.of());
    }

    public static AwardResult awarded(int xpAwarded, int bonusXp, int newLevel,
            long xpInLevel, long newCoins, int coinsEarned, boolean leveledUp,
            int level, int streakCurrent, int streakBest, int streakFreezes,
            long dailyXpToday, List<BadgeDto> newBadges) {
        return new AwardResult(true, xpAwarded, bonusXp, newLevel, xpInLevel, newCoins,
                coinsEarned, leveledUp, streakCurrent, streakBest, streakFreezes,
                dailyXpToday, false, newBadges);
    }
}
