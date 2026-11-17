package com.studexa.game.dto;

public record ShopPurchaseResult(
        String shopItemId,
        int price,
        long remainingCoins,
        boolean isFreeze) {
}
