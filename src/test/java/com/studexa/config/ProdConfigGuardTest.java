package com.studexa.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

class ProdConfigGuardTest {

    private ProdConfigGuard guard(String jwtSecret, boolean cookieSecure, String frontendUrl,
            String dbUrl, String dbUser, String dbPassword) {
        ProdConfigGuard guard = new ProdConfigGuard();
        ReflectionTestUtils.setField(guard, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(guard, "refreshCookieSecure", cookieSecure);
        ReflectionTestUtils.setField(guard, "frontendUrl", frontendUrl);
        ReflectionTestUtils.setField(guard, "datasourceUrl", dbUrl);
        ReflectionTestUtils.setField(guard, "datasourceUsername", dbUser);
        ReflectionTestUtils.setField(guard, "datasourcePassword", dbPassword);
        return guard;
    }

    private void run(ProdConfigGuard guard) {
        guard.run(new DefaultApplicationArguments(new String[0]));
    }

    @Test
    void happyPathDoesNotThrow() {
        run(guard("a-32-byte-prod-secret-that-is-long-enough-xyz", true,
                "https://studexa.app", "jdbc:postgresql://db:5432/studexa", "studexa", "password"));
    }

    @Test
    void secretTooShortThrows() {
        assertThatThrownBy(() -> run(guard("short", true, "https://studexa.app",
                "jdbc:postgresql://db:5432/studexa", "u", "p")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void secretDevPlaceholderThrows() {
        assertThatThrownBy(() -> run(guard("dev-only-secret", true, "https://studexa.app",
                "jdbc:postgresql://db:5432/studexa", "u", "p")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev placeholder");
    }

    @Test
    void cookieSecureFalseThrows() {
        assertThatThrownBy(() -> run(guard("a-32-byte-prod-secret-that-is-long-enough-xyz", false,
                "https://studexa.app", "jdbc:postgresql://db:5432/studexa", "u", "p")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_COOKIE_SECURE");
    }

    @Test
    void frontendUrlNotHttpsThrows() {
        assertThatThrownBy(() -> run(guard("a-32-byte-prod-secret-that-is-long-enough-xyz", true,
                "http://studexa.app", "jdbc:postgresql://db:5432/studexa", "u", "p")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https://");
    }

    @Test
    void missingDbUrlThrows() {
        assertThatThrownBy(() -> run(guard("a-32-byte-prod-secret-that-is-long-enough-xyz", true,
                "https://studexa.app", "", "u", "p")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL");
    }

    @Test
    void missingDbUserThrows() {
        assertThatThrownBy(() -> run(guard("a-32-byte-prod-secret-that-is-long-enough-xyz", true,
                "https://studexa.app", "jdbc:postgresql://db:5432/studexa", "", "p")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_USER");
    }

    @Test
    void missingDbPasswordThrows() {
        assertThatThrownBy(() -> run(guard("a-32-byte-prod-secret-that-is-long-enough-xyz", true,
                "https://studexa.app", "jdbc:postgresql://db:5432/studexa", "u", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void nullValuesAreTreatedAsMissing() {
        ProdConfigGuard guard = new ProdConfigGuard();
        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class);
    }
}
