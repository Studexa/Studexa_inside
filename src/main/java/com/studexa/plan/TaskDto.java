package com.studexa.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskDto(
        UUID id,
        String title,
        LocalDate taskDate,
        boolean done,
        Instant updatedAt,
        Instant createdAt
) {}
