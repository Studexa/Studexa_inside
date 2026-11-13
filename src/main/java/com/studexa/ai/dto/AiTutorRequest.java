package com.studexa.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiTutorRequest(
        @NotBlank(message = "Ask a question first.")
        @Size(max = 1200, message = "Questions must be 1200 characters or fewer.")
        String question,

        @Size(max = 20000, message = "Source text must be 20000 characters or fewer.")
        String sourceText,

        @Size(max = 200, message = "Source title must be 200 characters or fewer.")
        String sourceTitle) {
}
