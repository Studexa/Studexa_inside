package com.studexa.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.studexa.config.JwtProperties;
import com.studexa.user.User;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef";

    private JwtProperties properties;
    private JwtEncoder encoder;
    private JwtDecoder decoder;
    private RefreshTokenRepository refreshTokenRepository;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        properties = new JwtProperties(
                SECRET,
                "https://studexa.app",
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                false);
        encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        jwtService = new JwtService(encoder, refreshTokenRepository, properties, Clock.systemUTC());
    }

    @Test
    void accessTokenContainsUserIdentityAndExpiresInFifteenMinutes() {
        User user = User.withPassword("student@example.com", "hash");
        user.setId(UUID.randomUUID());

        IssuedTokens tokens = jwtService.issueTokens(user, UUID.randomUUID());
        Jwt jwt = decoder.decode(tokens.accessToken());

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("student@example.com");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now().plusSeconds(800));
        assertThat(jwt.getExpiresAt()).isBefore(Instant.now().plusSeconds(1000));
        assertThat(tokens.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void refreshTokenIsStoredOnlyAsAHash() {
        User user = User.withPassword("student@example.com", "hash");
        user.setId(UUID.randomUUID());

        IssuedTokens tokens = jwtService.issueTokens(user, UUID.randomUUID());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken stored = captor.getValue();

        assertThat(stored.getTokenHash())
                .isNotEqualTo(tokens.refreshToken())
                .isEqualTo(jwtService.hashRefreshToken(tokens.refreshToken()))
                .hasSize(64);
        assertThat(stored.getUser()).isEqualTo(user);
        assertThat(stored.getFamilyId()).isNotNull();
        assertThat(stored.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(29)));
        assertThat(stored.isRevoked()).isFalse();
    }
}
