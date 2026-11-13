package com.studexa.auth.dto;

import com.studexa.user.dto.ProfileResponse;
import com.studexa.user.dto.UserResponse;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user,
        ProfileResponse profile) {

    public static AuthResponse bearer(String accessToken, long expiresIn,
            UserResponse user, ProfileResponse profile) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user, profile);
    }
}
