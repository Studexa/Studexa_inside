package com.studexa.game;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class UserBadgeId {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "badge_code", length = 40)
    private String badgeCode;

    public UserBadgeId() {
    }

    public UserBadgeId(UUID userId, String badgeCode) {
        this.userId = userId;
        this.badgeCode = badgeCode;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getBadgeCode() {
        return badgeCode;
    }

    public void setBadgeCode(String badgeCode) {
        this.badgeCode = badgeCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof UserBadgeId that))
            return false;
        return Objects.equals(userId, that.userId) && Objects.equals(badgeCode, that.badgeCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, badgeCode);
    }
}
