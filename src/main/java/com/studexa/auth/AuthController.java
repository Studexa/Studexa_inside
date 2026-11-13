package com.studexa.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.auth.dto.AuthResponse;
import com.studexa.auth.dto.ForgotPasswordRequest;
import com.studexa.auth.dto.LoginRequest;
import com.studexa.auth.dto.RefreshRequest;
import com.studexa.auth.dto.RegisterRequest;
import com.studexa.auth.dto.ResetPasswordRequest;
import com.studexa.user.dto.ProfileResponse;
import com.studexa.user.dto.UserResponse;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Email/password authentication. The refresh token travels in an HttpOnly
 * cookie; the access token is returned in the JSON body.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieService refreshCookieService;

    public AuthController(AuthService authService, RefreshCookieService refreshCookieService) {
        this.authService = authService;
        this.refreshCookieService = refreshCookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthResult result = authService.register(request);
        refreshCookieService.set(response, result.tokens().refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResult result = authService.login(request);
        refreshCookieService.set(response, result.tokens().refreshToken());
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false) String cookieToken,
            @RequestBody(required = false) RefreshRequest request,
            HttpServletResponse response) {
        String rawToken = cookieToken != null ? cookieToken : tokenFromBody(request);
        AuthResult result = authService.refresh(rawToken);
        refreshCookieService.set(response, result.tokens().refreshToken());
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false) String cookieToken,
            @RequestBody(required = false) RefreshRequest request,
            HttpServletResponse response) {
        String rawToken = cookieToken != null ? cookieToken : tokenFromBody(request);
        authService.logout(rawToken);
        refreshCookieService.clear(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    private static String tokenFromBody(RefreshRequest request) {
        return request == null ? null : request.refreshToken();
    }

    private static AuthResponse toResponse(AuthResult result) {
        return AuthResponse.bearer(
                result.tokens().accessToken(),
                result.tokens().expiresInSeconds(),
                UserResponse.from(result.user()),
                ProfileResponse.from(result.profile()));
    }
}
