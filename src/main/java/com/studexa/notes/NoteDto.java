package com.studexa.notes;

import java.time.Instant;
import java.util.UUID;

public record NoteDto(
        UUID id,
        String title,
        String content,
        String subject,
        Instant updatedAt,
        Instant createdAt
) {}
