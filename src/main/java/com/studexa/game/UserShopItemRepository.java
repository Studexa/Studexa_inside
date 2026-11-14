package com.studexa.game;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserShopItemRepository extends JpaRepository<UserShopItem, UUID> {

    List<UserShopItem> findByUserId(UUID userId);

    @Query("select count(u) from UserShopItem u where u.userId = :userId and u.shopItemId = :shopItemId")
    boolean existsByUserIdAndShopItemId(@Param("userId") UUID userId, @Param("shopItemId") String shopItemId);
}
