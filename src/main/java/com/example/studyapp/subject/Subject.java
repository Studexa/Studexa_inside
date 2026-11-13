package com.example.studyapp.subject;

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
 * A school subject used to group a student's decks, notes and exams.
 * The owner is stored as a plain user id so every query can be scoped by it.
 */
@Entity
@Table(name = "subjects")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subject {

    public static final String DEFAULT_COLOR = "#6366f1";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String color;

    @Column(length = 32)
    private String icon;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Subject(UUID userId, String name, String color, String icon) {
        this.userId = userId;
        this.name = name;
        this.color = color;
        this.icon = icon;
        this.archived = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Subject create(UUID userId, String name, String color, String icon) {
        return new Subject(userId, name, color, icon);
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
