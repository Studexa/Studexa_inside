package com.example.studyapp.auth;

import java.time.Instant;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.example.studyapp.config.JwtProperties;
import com.example.studyapp.user.User;

/**
 * Creates short-lived access tokens and stores hashed refresh tokens.
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties properties;

    public JwtService(JwtEncoder encoder,
            RefreshTokenRepository refreshTokenRepository,
            JwtProperties properties) {
        this.encoder = encoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
    }

    public IssuedTokens issueTokens(User user) {
        Instant now = Instant.now();
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
