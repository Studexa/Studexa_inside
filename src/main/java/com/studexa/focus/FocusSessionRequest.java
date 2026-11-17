package com.studexa.focus;

import java.time.Instant;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FocusSessionRequest(
        @NotNull @Min(1) Integer minutes,
        @NotNull Instant completedAt
) {}