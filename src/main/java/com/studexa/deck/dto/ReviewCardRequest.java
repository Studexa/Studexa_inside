package com.studexa.deck.dto;

import com.studexa.deck.ReviewRating;

import jakarta.validation.constraints.NotNull;

public record ReviewCardRequest(
        @NotNull(message = "Choose how well you remembered the card.")
        ReviewRating rating) {
}
