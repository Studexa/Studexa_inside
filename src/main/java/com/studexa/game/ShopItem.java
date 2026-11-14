package com.studexa.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shop_items")
public class ShopItem {

    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Column(name = "name_km", nullable = false, length = 100)
    private String nameKm;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false)
    private int price;

    @Column(name = "accessory_type", length = 20)
    private String accessoryType;

    @Column(nullable = false)
    private boolean active;

    public ShopItem() {
    }

    public ShopItem(String id, String nameEn, String nameKm, String kind, int price,
            String accessoryType, boolean active) {
        this.id = id;
        this.nameEn = nameEn;
        this.nameKm = nameKm;
        this.kind = kind;
        this.price = price;
        this.accessoryType = accessoryType;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public String getNameKm() {
        return nameKm;
    }

    public void setNameKm(String nameKm) {
        this.nameKm = nameKm;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public String getAccessoryType() {
        return accessoryType;
    }

    public void setAccessoryType(String accessoryType) {
        this.accessoryType = accessoryType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
