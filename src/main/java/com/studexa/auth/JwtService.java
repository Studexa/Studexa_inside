package com.studexa.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.studexa.config.JwtProperties;
import com.studexa.user.User;

/**
 * Creates short-lived access tokens and stores hashed refresh tokens.
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder,
            RefreshTokenRepository refreshTokenRepository,
            JwtProperties properties,
            Clock clock) {
        this.encoder = encoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedTokens issueTokens(User user, UUID familyId) {
        Instant now = clock.instant();
        Instant accessExpiresAt = now.plus(properties.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(accessExpiresAt)
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();

        String accessToken = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        String rawRefreshToken = TokenHasher.newRawToken();
        Instant refreshExpiresAt = now.plus(properties.refreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(
                user,
                TokenHasher.sha256(rawRefreshToken),
                familyId,
                refreshExpiresAt));

        return new IssuedTokens(
                accessToken,
                rawRefreshToken,
                accessExpiresAt,
                properties.accessTokenTtl().toSeconds(),
                refreshExpiresAt);
    }

    public String hashRefreshToken(String rawToken) {
        return TokenHasher.sha256(rawToken);
    }
}
