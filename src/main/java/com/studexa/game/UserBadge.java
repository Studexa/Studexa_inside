package com.studexa.game;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_badges")
public class UserBadge {

    @EmbeddedId
    private UserBadgeId id;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    public UserBadge() {
    }

    public UserBadgeId getId() {
        return id;
    }

    public void setId(UserBadgeId id) {
        this.id = id;
    }

    public Instant getEarnedAt() {
        return earnedAt;
    }

    public void setEarnedAt(Instant earnedAt) {
        this.earnedAt = earnedAt;
    }

    public static UserBadge create(UUID userId, String badgeCode, Instant now) {
        UserBadge ub = new UserBadge();
        ub.id = new UserBadgeId(userId, badgeCode);
        ub.earnedAt = now;
        return ub;
    }
}
