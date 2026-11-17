package com.studexa.boss;

import java.time.Instant;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BossFightRequest(
        @NotNull Boolean won,
        @NotNull @Min(1) @Max(50) Integer questionsCount,
        @NotNull @Min(0) @Max(10) Integer heartsRemaining,
        @NotNull Instant completedAt
) {}