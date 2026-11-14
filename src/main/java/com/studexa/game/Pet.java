package com.studexa.game;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The student's study buddy. Grows with level and changes mood based on study
 * activity. Accessories are bought with coins in the shop.
 */
@Entity
@Table(name = "pets")
public class Pet {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String species;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String stage;

    @Column(nullable = false, length = 20)
    private String mood;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> accessories;

    @Column(name = "equipped_accessory", length = 20)
    private String equippedAccessory;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Pet() {
    }

    public Pet(UUID userId, String species) {
        this.userId = userId;
        this.species = species;
        this.name = "Buddy";
        this.stage = "baby";
        this.mood = "sleepy";
        this.accessories = List.of();
        this.equippedAccessory = null;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getSpecies() {
        return species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public List<String> getAccessories() {
        return accessories;
    }

    public void setAccessories(List<String> accessories) {
        this.accessories = accessories;
    }

    public String getEquippedAccessory() {
        return equippedAccessory;
    }

    public void setEquippedAccessory(String equippedAccessory) {
        this.equippedAccessory = equippedAccessory;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Compute the buddy stage from the student's level.
     */
    public static String stageForLevel(int level) {
        if (level >= 20) return "legend";
        if (level >= 10) return "grown";
        if (level >= 5) return "young";
        return "baby";
    }

    /**
     * Compute the buddy mood: happy if studied today, sleepy if not,
     * sparkling if streak is 7+.
     */
    public static String moodFor(boolean studiedToday, int streak) {
        if (streak >= 7) return "sparkling";
        return studiedToday ? "happy" : "sleepy";
    }

    /**
     * Map a shop accessory type to the accessory slot value.
     */
    public static String accessorySlot(String accessoryType) {
        return switch (accessoryType) {
            case "hat" -> "hat";
            case "glasses" -> "glasses";
            case "scarf" -> "scarf";
            case "cape" -> "cape";
            default -> null;
        };
    }
}
