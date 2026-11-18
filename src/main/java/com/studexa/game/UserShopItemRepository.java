package com.studexa.game;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserShopItemRepository extends JpaRepository<UserShopItem, UUID> {

    List<UserShopItem> findByUserId(UUID userId);

    boolean existsByUserIdAndShopItemId(UUID userId, String shopItemId);
}
