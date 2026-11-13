package com.studexa.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional Google login settings from {@code app.oauth.google.*}.
 * Disabled by default so local development needs no Google credentials.
 */
@ConfigurationProperties(prefix = "app.oauth.google")
public record GoogleOAuthProperties(boolean enabled, String clientId, String clientSecret) {
}
