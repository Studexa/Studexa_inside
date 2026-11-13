package com.studexa.deck.dto;

import java.util.UUID;

import jakarta.validation.constraints.Size;

public record UpdateDeckRequest(
        @Size(max = 160, message = "Deck title must be 160 characters or fewer.")
        String title,

        @Size(max = 2000, message = "Description must be 2000 characters or fewer.")
        String description,

        UUID subjectId,

        @Size(max = 20, message = "Color must be 20 characters or fewer.")
        String color,

        @Size(max = 32, message = "Icon must be 32 characters or fewer.")
        String icon) {
}
