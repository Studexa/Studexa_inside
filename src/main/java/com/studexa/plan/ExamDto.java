package com.studexa.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExamDto(
        UUID id,
        String title,
        String subject,
        LocalDate examDate,
        String importance,
        Instant updatedAt,
        Instant createdAt
) {}
