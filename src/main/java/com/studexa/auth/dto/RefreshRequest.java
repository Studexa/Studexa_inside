package com.studexa.auth.dto;

/**
 * Optional body fallback for clients that cannot use the refresh cookie.
 */
public record RefreshRequest(String refreshToken) {
}
