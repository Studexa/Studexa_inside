package com.studexa.game.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PetDto(
        UUID userId,
        String species,
        String name,
        String stage,
        String mood,
        List<String> accessories,
        String equippedAccessory,
        Instant createdAt,
        Instant updatedAt) {
}
