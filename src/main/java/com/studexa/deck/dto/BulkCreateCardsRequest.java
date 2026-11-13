package com.studexa.deck.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record BulkCreateCardsRequest(
        @NotEmpty(message = "Add at least one card.")
        @Size(max = 100, message = "Add at most 100 cards at a time.")
        List<@Valid CreateCardRequest> cards) {
}
