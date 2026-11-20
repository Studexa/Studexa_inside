package com.studexa.ai.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiTutorRequest(
        @NotBlank(message = "Ask a question first.")
        @Size(max = 1200, message = "Questions must be 1200 characters or fewer.")
        String question,

        @Size(max = 20000, message = "Source text must be 20000 characters or fewer.")
        String sourceText,

        @Size(max = 200, message = "Source title must be 200 characters or fewer.")
        String sourceTitle,

        @Size(max = 3) List<@NotNull @Valid Turn> history) {

    public AiTutorRequest(String question, String sourceText, String sourceTitle) {
        this(question, sourceText, sourceTitle, null);
    }

    public record Turn(
            @NotBlank @Size(max = 1200) String question,
            @NotBlank @Size(max = 1200) String answer) {
    }
}
