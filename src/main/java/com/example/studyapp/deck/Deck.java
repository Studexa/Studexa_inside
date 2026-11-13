package com.example.studyapp.deck;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A collection of flashcards. The owner is always a plain user id and every
 * query is scoped by it.
 */
@Entity
@Table(name = "decks")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
}
