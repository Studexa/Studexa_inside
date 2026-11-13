package com.example.studyapp.deck.dto;

import com.example.studyapp.deck.ReviewRating;

import jakarta.validation.constraints.NotNull;

public record ReviewCardRequest(
        @NotNull(message = "Choose how well you remembered the card.")
        ReviewRating rating) {
}
