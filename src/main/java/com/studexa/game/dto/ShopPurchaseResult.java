package com.studexa.game;

public record ShopPurchaseResult(
        String shopItemId,
        int price,
        long remainingCoins,
        boolean isFreeze) {
}
