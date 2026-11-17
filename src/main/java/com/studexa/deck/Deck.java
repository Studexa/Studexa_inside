package com.studexa.deck;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A collection of flashcards. The owner is always a plain user id and every
 * query is scoped by it.
 */
@Entity
@Table(name = "decks")
public class Deck {

    public static final String DEFAULT_COLOR = "#6366f1";
    public static final String DEFAULT_ICON = "📚";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 20)
    private String color;

    @Column(length = 32)
    private String icon;

    @Column(name = "is_public", nullable = false)
    private boolean publicDeck;

    @Column(name = "copied_from")
    private UUID copiedFrom;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Deck() {
    }

    private Deck(UUID userId, UUID subjectId, String title, String description, String color, String icon) {
        this.userId = userId;
        this.subjectId = subjectId;
        this.title = title;
        this.description = description;
        this.color = color;
        this.icon = icon;
        this.publicDeck = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Deck create(UUID userId, UUID subjectId, String title, String description,
            String color, String icon) {
        return new Deck(userId, subjectId, title, description, color, icon);
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
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

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public boolean isPublicDeck() {
        return publicDeck;
    }

    public void setPublicDeck(boolean publicDeck) {
        this.publicDeck = publicDeck;
    }

    public UUID getCopiedFrom() {
        return copiedFrom;
    }

    public void setCopiedFrom(UUID copiedFrom) {
        this.copiedFrom = copiedFrom;
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
}
