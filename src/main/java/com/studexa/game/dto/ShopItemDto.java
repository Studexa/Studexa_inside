package com.studexa.game.dto;

public record ShopItemDto(
        String id,
        String nameEn,
        String nameKm,
        String kind,
        int price,
        String accessoryType,
        boolean active) {
}
