package com.studexa.deck.dto;

import java.time.Instant;

public record DailyReviewProgress(long reviewedCards, Instant resetsAt) {}
