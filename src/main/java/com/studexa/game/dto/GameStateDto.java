package com.studexa.game.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GameStateDto(
        UUID userId,
        long xpTotal,
        int level,
        long xpInLevel,
        long coins,
        int streakCurrent,
        int streakBest,
        int streakFreezes,
        LocalDate lastActiveDate,
        long dailyXpToday,
        LocalDate dailyXpDate,
        int xpToNextLevel) {
}
