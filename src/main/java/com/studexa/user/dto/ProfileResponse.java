package com.studexa.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.studexa.user.Profile;

public record ProfileResponse(
        UUID userId,
        String nickname,
        String avatar,
        String language,
        String levelOfStudy,
        String timezone,
        int dailyGoalCards,
        int dailyGoalMinutes,
        Instant updatedAt) {

    public static ProfileResponse from(Profile profile) {
        return new ProfileResponse(
                profile.getUserId(),
                profile.getNickname(),
                profile.getAvatar(),
                profile.getLanguage(),
                profile.getLevelOfStudy(),
                profile.getTimezone(),
                profile.getDailyGoalCards(),
                profile.getDailyGoalMinutes(),
                profile.getUpdatedAt());
    }
}
