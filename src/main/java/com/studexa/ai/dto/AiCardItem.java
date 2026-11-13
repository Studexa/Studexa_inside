package com.studexa.ai.dto;

import java.util.List;

import com.studexa.deck.CardType;

public record AiCardItem(
        CardType type,
        String front,
        String back,
        List<String> choices,
        String explanation,
        String sourceTitle,
        String sourceExcerpt) {
}
