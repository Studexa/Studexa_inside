package com.studexa.deck.dto;

import java.util.List;
import java.util.Map;

import com.studexa.deck.CardType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCardRequest(
        @NotNull(message = "Card type is required.")
        CardType type,

        @NotBlank(message = "Card front is required.")
        @Size(max = 5000, message = "Card front must be 5000 characters or fewer.")
        String front,

        @NotBlank(message = "Card back is required.")
        @Size(max = 5000, message = "Card back must be 5000 characters or fewer.")
        String back,

        Map<String, Object> extra,

        @Size(max = 20, message = "A card can have at most 20 tags.")
        List<String> tags,

        @Size(max = 200, message = "Source title must be 200 characters or fewer.")
        String sourceTitle,

        @Size(max = 1000, message = "Source excerpt must be 1000 characters or fewer.")
        String sourceExcerpt) {
}
