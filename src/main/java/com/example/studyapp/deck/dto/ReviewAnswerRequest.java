package com.example.studyapp.deck.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.example.studyapp.deck.ReviewRating;

public record ReviewAnswerRequest(
        UUID cardId,
        @NotNull(message = "Choose how well you remembered the card.")
        ReviewRating rating,
        @NotNull(message = "Time spent answering is required.")
        Integer msTaken,
        String clientReviewId) {
}
