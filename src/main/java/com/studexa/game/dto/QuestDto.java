package com.studexa.game.dto;

import java.time.LocalDate;
import java.util.UUID;

public record QuestDto(
        UUID id,
        String kind,
        String code,
        String nameKey,
        int target,
        int progress,
        int rewardXp,
        int rewardCoins,
        LocalDate questDate,
        boolean completed) {
}
