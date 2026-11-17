package com.studexa.boss;

import java.time.Instant;
import java.util.UUID;

public record BossFightDto(
        UUID id,
        Boolean won,
        Integer questionsCount,
        Integer heartsRemaining,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {}