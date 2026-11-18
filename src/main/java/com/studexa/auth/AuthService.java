package com.studexa.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.text.Normalizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.auth.dto.LoginRequest;
import com.studexa.auth.dto.RegisterRequest;
import com.studexa.auth.dto.ResetPasswordRequest;
import com.studexa.common.ApiException;
import com.studexa.user.Profile;
import com.studexa.user.ProfileRepository;
import com.studexa.user.User;
import com.studexa.user.UserRepository;

/**
 * Registration, login, token rotation and password reset.
 * All reads/writes are scoped to the user resolved from the request,
 * never to an id supplied by the client.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String frontendUrl;
    private final Clock clock;
    private final boolean betaEnabled;
    private final boolean betaRegistrationOpen;
    private final JdbcTemplate jdbc;

    public AuthService(UserRepository userRepository,
            ProfileRepository profileRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.frontend-url}") String frontendUrl,
            Clock clock,
            @Value("${app.beta.enabled:false}") boolean betaEnabled,
            @Value("${app.beta.registration-open:true}") boolean betaRegistrationOpen,
            JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.frontendUrl = frontendUrl;
        this.clock = clock;
        this.betaEnabled = betaEnabled;
        this.betaRegistrationOpen = betaRegistrationOpen;
        this.jdbc = jdbc;
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (betaEnabled) {
            if (!betaRegistrationOpen) {
                throw ApiException.serviceUnavailable("BETA_ENROLLMENT_CLOSED", "Beta enrollment is closed.");
            }
            redeemInvite(email, request.inviteCode(), request.adultConfirmed());
        }
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists.");
        }

        User user = userRepository.save(
                User.withPassword(email, passwordEncoder.encode(request.password())));

        String nickname = hasText(request.nickname()) ? normalizeText(request.nickname().trim()) : defaultNickname(email);
        String language = hasText(request.language()) ? request.language() : Profile.DEFAULT_LANGUAGE;
        Profile profile = profileRepository.save(new Profile(user, nickname, language));

        return result(user, profile, jwtService.issueTokens(user, UUID.randomUUID()));
    }

    private void redeemInvite(String email, String code, Boolean adultConfirmed) {
        if (!Boolean.TRUE.equals(adultConfirmed)) {
            throw ApiException.badRequest("BETA_ADULT_REQUIRED", "Confirm that you are at least 18.");
        }
        Instant now = clock.instant();
        if (!hasText(code) || jdbc.update("""
                UPDATE beta_invites SET redeemed_at = ?
                WHERE code_hash = ? AND email = ? AND expires_at > ?
                  AND revoked_at IS NULL AND redeemed_at IS NULL
                """, java.sql.Timestamp.from(now), TokenHasher.sha256(code.trim()),
                email, java.sql.Timestamp.from(now)) != 1) {
            throw ApiException.badRequest("BETA_INVALID_INVITE", "Use a valid invite code for this email address.");
        }
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(AuthService::invalidCredentials);

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        Profile profile = requireProfile(user);
        return result(user, profile, jwtService.issueTokens(user, UUID.randomUUID()));
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        if (!hasText(rawRefreshToken)) {
            throw invalidToken();
        }

        RefreshToken token = refreshTokenRepository
                .findByTokenHash(jwtService.hashRefreshToken(rawRefreshToken))
                .orElseThrow(AuthService::invalidToken);

        // Token reuse detection: if the presented token is already revoked,
        // revoke the entire family and reject. This stops refresh-token
        // replay attacks from compromising the user's session.
        if (token.isRevoked()) {
            UUID familyId = token.getFamilyId();
            if (familyId != null) {
                refreshTokenRepository.revokeAllForFamily(familyId);
            }
            throw invalidToken();
        }

        if (!token.isUsable(clock.instant())) {
            throw invalidToken();
        }

        UUID familyId = token.getFamilyId() != null ? token.getFamilyId() : UUID.randomUUID();
        token.setRevoked(true);
        User user = token.getUser();
        Profile profile = requireProfile(user);
        return result(user, profile, jwtService.issueTokens(user, familyId));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (!hasText(rawRefreshToken)) {
            return;
        }
        refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(rawRefreshToken))
                .ifPresent(token -> {
                    UUID familyId = token.getFamilyId();
                    if (familyId != null) {
                        refreshTokenRepository.revokeAllForFamily(familyId);
                    } else {
                        token.setRevoked(true);
                    }
                });
    }

    /**
     * Always succeeds from the client's point of view, so the response cannot
     * be used to discover which emails have an account. The reset link is
     * logged until real email sending is added in Step 20.
     */
    @Transactional
    public void forgotPassword(String email) {
        requirePasswordResetAvailable();
        userRepository.findByEmail(normalizeEmail(email))
                .filter(user -> user.getPasswordHash() != null)
                .ifPresent(user -> {
                    String rawToken = TokenHasher.newRawToken();
                    passwordResetTokenRepository.save(new PasswordResetToken(
                            user,
                            TokenHasher.sha256(rawToken),
                            clock.instant().plus(PASSWORD_RESET_TTL)));
                    log.info("Password reset link for {}: {}/reset-password?token={}",
                            user.getEmail(), frontendUrl, rawToken);
                });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        requirePasswordResetAvailable();
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(TokenHasher.sha256(request.token()))
                .orElseThrow(AuthService::invalidToken);

        if (!token.isUsable(clock.instant())) {
            throw invalidToken();
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        token.setUsed(true);
        refreshTokenRepository.revokeAllForUser(user.getId());
    }

    private void requirePasswordResetAvailable() {
        if (betaEnabled) {
            throw ApiException.notFound("Contact the beta organizer for help with your account.");
        }
    }

    @Transactional
    public AuthResult loginWithGoogle(String email, String googleId, String displayName) {
        if (!hasText(email) || !hasText(googleId)) {
            throw ApiException.badRequest("GOOGLE_PROFILE_INCOMPLETE",
                    "Google did not return an email address for this account.");
        }

        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByGoogleId(googleId)
                .or(() -> userRepository.findByEmail(normalizedEmail))
                .orElseGet(() -> userRepository.save(User.withGoogle(normalizedEmail, googleId)));

        if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
        }

        Profile profile = profileRepository.findById(user.getId())
                .orElseGet(() -> {
                    String nickname = hasText(displayName) ? normalizeText(displayName.trim()) : defaultNickname(normalizedEmail);
                    if (nickname.length() > 50) {
                        nickname = nickname.substring(0, 50);
                    }
                    return profileRepository.save(new Profile(user, nickname, Profile.DEFAULT_LANGUAGE));
                });

        return result(user, profile, jwtService.issueTokens(user, UUID.randomUUID()));
    }

    private Profile requireProfile(User user) {
        return profileRepository.findById(user.getId())
                .orElseThrow(() -> ApiException.notFound("Profile not found."));
    }

    private static AuthResult result(User user, Profile profile, IssuedTokens tokens) {
        return new AuthResult(tokens, user, profile);
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect.");
    }

    private static ApiException invalidToken() {
        return ApiException.unauthorized("INVALID_TOKEN", "Your session has expired. Please log in again.");
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String defaultNickname(String email) {
        int at = email.indexOf('@');
        String base = at > 0 ? email.substring(0, at) : email;
        if (base.isBlank()) {
            base = "Learner";
        }
        return base.length() > 50 ? base.substring(0, 50) : base;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Normalizes text to Unicode NFC so visually identical Khmer strings
     * compare equal (BUG-G03).
     */
    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }
}
