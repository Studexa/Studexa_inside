package com.studexa.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.studexa.config.JwtProperties;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Single place that writes and clears the refresh-token cookie.
 * Scoped to /api/auth so it is not sent on every API call.
 */
@Component
public class RefreshCookieService {

    public static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";
    private static final String COOKIE_SAME_SITE = "Lax";

    private final JwtProperties jwtProperties;

    public RefreshCookieService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public void set(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(jwtProperties.refreshCookieSecure())
                .sameSite(COOKIE_SAME_SITE)
                .path(COOKIE_PATH)
                .maxAge(jwtProperties.refreshTokenTtl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(jwtProperties.refreshCookieSecure())
                .sameSite(COOKIE_SAME_SITE)
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
