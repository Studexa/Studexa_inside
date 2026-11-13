package com.studexa.subject.dto;

import java.time.Instant;
import java.util.UUID;

import com.studexa.subject.Subject;

public record SubjectResponse(
        UUID id,
        String name,
        String color,
        String icon,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {

    public static SubjectResponse from(Subject subject) {
        return new SubjectResponse(
                subject.getId(),
                subject.getName(),
                subject.getColor(),
                subject.getIcon(),
                subject.isArchived(),
                subject.getCreatedAt(),
                subject.getUpdatedAt());
    }
}
