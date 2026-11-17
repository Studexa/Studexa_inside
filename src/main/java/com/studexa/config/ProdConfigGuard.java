package com.studexa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-fast guard for production startup.
 * <p>
 * Runs only when the {@code prod} profile is active and throws if the
 * deployment is missing required secrets or is using unsafe defaults, so a
 * misconfigured container fails fast instead of running insecurely.
 */
@Component
@Profile("prod")
public class ProdConfigGuard implements ApplicationRunner {

    private static final String DEV_PLACEHOLDER_PREFIX = "dev-only";
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.jwt.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    @Value("${app.frontend-url:}")
    private String frontendUrl;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Override
    public void run(ApplicationArguments args) {
        checkJwtSecret();
        checkCookieSecure();
        checkFrontendUrl();
        checkDatabase();
    }

    private void checkJwtSecret() {
        if (jwtSecret != null && jwtSecret.startsWith(DEV_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException(
                    "JWT_SECRET (app.jwt.secret) must not be the dev placeholder in production");
        }
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET (app.jwt.secret) must be at least " + MIN_SECRET_BYTES + " bytes in production");
        }
    }

    private void checkCookieSecure() {
        if (!refreshCookieSecure) {
            throw new IllegalStateException(
                    "JWT_COOKIE_SECURE (app.jwt.refresh-cookie-secure) must be true in production");
        }
    }

    private void checkFrontendUrl() {
        if (frontendUrl == null || !frontendUrl.startsWith("https://")) {
            throw new IllegalStateException(
                    "FRONTEND_URL (app.frontend-url) must be an https:// URL in production, was: " + frontendUrl);
        }
    }

    private void checkDatabase() {
        if (datasourceUrl == null || datasourceUrl.isBlank()) {
            throw new IllegalStateException(
                    "DB_URL (spring.datasource.url) is required in production");
        }
        if (datasourceUsername == null || datasourceUsername.isBlank()) {
            throw new IllegalStateException(
                    "DB_USER (spring.datasource.username) is required in production");
        }
        if (datasourcePassword == null || datasourcePassword.isBlank()) {
            throw new IllegalStateException(
                    "DB_PASSWORD (spring.datasource.password) is required in production");
        }
    }
}
