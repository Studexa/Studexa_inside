package com.example.studyapp.deck;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A flashcard. Source fields keep a short, verifiable reference to the note or
 * pasted material the card was generated from.
 */
@Entity
@Table(name = "cards")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "deck_id", nullable = false)
    private UUID deckId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardType type;

    @Column(nullable = false, columnDefinition = "text")
    private String front;

    @Column(nullable = false, columnDefinition = "text")
    private String back;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> extra;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "media_url", columnDefinition = "text")
    private String mediaUrl;

    @Column(nullable = false)
    private boolean starred;

    @Column(nullable = false)
    private boolean suspended;

    @Column(name = "source_title", length = 200)
    private String sourceTitle;

    @Column(name = "source_excerpt", columnDefinition = "text")
    private String sourceExcerpt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Card(UUID deckId, UUID userId, CardType type, String front, String back,
            Map<String, Object> extra, List<String> tags, String sourceTitle, String sourceExcerpt) {
        this.deckId = deckId;
        this.userId = userId;
        this.type = type;
        this.front = front;
        this.back = back;
        this.extra = extra == null ? Map.of() : extra;
        this.tags = tags == null ? List.of() : tags;
        this.sourceTitle = sourceTitle;
        this.sourceExcerpt = sourceExcerpt;
        this.starred = false;
        this.suspended = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Card create(UUID deckId, UUID userId, CardType type, String front, String back,
            Map<String, Object> extra, List<String> tags, String sourceTitle, String sourceExcerpt) {
        return new Card(deckId, userId, type, front, back, extra, tags, sourceTitle, sourceExcerpt);
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
