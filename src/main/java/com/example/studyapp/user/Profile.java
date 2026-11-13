package com.example.studyapp.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Student-facing profile. Shares its primary key with {@link User}.
 */
@Entity
@Table(name = "profiles")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile {

    public static final String DEFAULT_TIMEZONE = "Asia/Phnom_Penh";
    public static final String DEFAULT_LANGUAGE = "en";

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 100)
    private String avatar;

    @Column(nullable = false, length = 5)
    private String language;

    @Column(name = "level_of_study", length = 50)
    private String levelOfStudy;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "daily_goal_cards", nullable = false)
    private int dailyGoalCards;

    @Column(name = "daily_goal_minutes", nullable = false)
    private int dailyGoalMinutes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Profile(User user, String nickname, String language) {
        this.user = user;
        this.nickname = nickname;
        this.language = language;
        this.timezone = DEFAULT_TIMEZONE;
        this.dailyGoalCards = 10;
        this.dailyGoalMinutes = 10;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
