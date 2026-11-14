package com.studexa.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "badges")
public class Badge {

    @Id
    @Column(length = 40)
    private String code;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Column(name = "name_km", nullable = false, length = 100)
    private String nameKm;

    @Column(name = "description_en", nullable = false, columnDefinition = "text")
    private String descriptionEn;

    @Column(name = "description_km", nullable = false, columnDefinition = "text")
    private String descriptionKm;

    @Column(name = "coins_reward", nullable = false)
    private int coinsReward;

    public Badge() {
    }

    public Badge(String code, String nameEn, String nameKm, String descriptionEn,
            String descriptionKm, int coinsReward) {
        this.code = code;
        this.nameEn = nameEn;
        this.nameKm = nameKm;
        this.descriptionEn = descriptionEn;
        this.descriptionKm = descriptionKm;
        this.coinsReward = coinsReward;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    public String getDescriptionKm() {
        return descriptionKm;
    }

    public void setDescriptionKm(String descriptionKm) {
        this.descriptionKm = descriptionKm;
    }

    public int getCoinsReward() {
        return coinsReward;
    }

    public void setCoinsReward(int coinsReward) {
        this.coinsReward = coinsReward;
    }
}
