package com.studexa.user;

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

/**
 * Student-facing profile. Shares its primary key with {@link User}.
 */
@Entity
@Table(name = "profiles")
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

    public Profile() {
    }

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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLevelOfStudy() {
        return levelOfStudy;
    }

    public void setLevelOfStudy(String levelOfStudy) {
        this.levelOfStudy = levelOfStudy;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public int getDailyGoalCards() {
        return dailyGoalCards;
    }

    public void setDailyGoalCards(int dailyGoalCards) {
        this.dailyGoalCards = dailyGoalCards;
    }

    public int getDailyGoalMinutes() {
        return dailyGoalMinutes;
    }

    public void setDailyGoalMinutes(int dailyGoalMinutes) {
        this.dailyGoalMinutes = dailyGoalMinutes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
