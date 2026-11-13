package com.studexa.auth;

import java.time.Instant;

/**
 * Result of issuing a new access token and its matching refresh token.
 * The raw refresh token is only returned here and must be sent as a cookie.
 */
public record IssuedTokens(
        String accessToken,
        String refreshToken,
        Instant accessExpiresAt,
        long expiresInSeconds,
        Instant refreshExpiresAt) {
}
