package com.studexa.game.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record XpEventDto(
        UUID id,
        String source,
        int amount,
        Instant createdAt,
        UUID referenceId) {
}
