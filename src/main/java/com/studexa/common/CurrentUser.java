package com.studexa.common;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the user id from the access token. Controllers never trust a user id
 * supplied in a path, body or query parameter.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ex) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Authentication required.");
        }
    }
}
