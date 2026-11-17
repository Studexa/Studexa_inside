package com.studexa.focus;

import java.time.Instant;
import java.util.UUID;

public record FocusSessionDto(
        UUID id,
        Integer minutes,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {}