package com.studexa.ai.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AiGenerateRequest(
        @NotNull(message = "Choose what to generate.")
        AiTask task,

        UUID deckId,

        @Size(max = 20000, message = "Source text must be 20000 characters or fewer.")
        String sourceText,

        @Size(max = 200, message = "Source title must be 200 characters or fewer.")
        String sourceTitle,

        @Min(value = 1, message = "Ask for at least one item.")
        @Max(value = 15, message = "Ask for at most 15 items at a time.")
        Integer count,

        @Pattern(regexp = "en|km", message = "Language must be en or km.")
        String language) {
}
