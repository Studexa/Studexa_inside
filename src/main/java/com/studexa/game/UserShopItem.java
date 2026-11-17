package com.studexa.game;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_shop_items")
public class UserShopItem {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "shop_item_id", nullable = false, length = 40)
    private String shopItemId;

    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    public UserShopItem() {
    }

    public UserShopItem(UUID userId, String shopItemId, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.shopItemId = shopItemId;
        this.purchasedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getShopItemId() {
        return shopItemId;
    }

    public void setShopItemId(String shopItemId) {
        this.shopItemId = shopItemId;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }

    public void setPurchasedAt(Instant purchasedAt) {
        this.purchasedAt = purchasedAt;
    }
}
