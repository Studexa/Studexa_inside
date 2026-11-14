package com.studexa.game;

import java.time.Instant;
import java.util.UUID;

public record UserShopItemDto(
        UUID id,
        String shopItemId,
        Instant purchasedAt) {
}
