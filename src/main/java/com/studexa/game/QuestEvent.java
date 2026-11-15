package com.studexa.game;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Audit trail of quest progress events. Used for idempotency: the same
 * (quest_id, source, reference_id) combination can only be applied once.
 */
@Entity
@Table(name = "quest_events")
public class QuestEvent {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "quest_id", nullable = false)
    private UUID questId;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public QuestEvent() {
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

    public UUID getQuestId() {
        return questId;
    }

    public void setQuestId(UUID questId) {
        this.questId = questId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(UUID referenceId) {
        this.referenceId = referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public static QuestEvent create(UUID id, UUID userId, UUID questId, String source,
            UUID referenceId, Instant now) {
        QuestEvent event = new QuestEvent();
        event.id = id;
        event.userId = userId;
        event.questId = questId;
        event.source = source;
        event.referenceId = referenceId;
        event.createdAt = now;
        return event;
    }
}
