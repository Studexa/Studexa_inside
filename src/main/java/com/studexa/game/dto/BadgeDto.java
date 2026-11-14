package com.studexa.game.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BadgeDto(
        String code,
        String nameEn,
        String nameKm,
        String descriptionEn,
        String descriptionKm,
        int coinsReward,
        boolean earned) {
}
