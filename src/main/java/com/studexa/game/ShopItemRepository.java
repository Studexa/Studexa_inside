package com.studexa.game;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopItemRepository extends JpaRepository<ShopItem, String> {

    List<ShopItem> findByActiveOrderByPriceAsc(boolean active);

    List<ShopItem> findByKindAndActiveOrderByPriceAsc(String kind, boolean active);
}
