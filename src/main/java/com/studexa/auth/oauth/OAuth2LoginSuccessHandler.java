package com.studexa.auth.oauth;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.studexa.auth.AuthResult;
import com.studexa.auth.AuthService;
import com.studexa.auth.RefreshCookieService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * After Google login, creates/finds the local user, sets the refresh cookie,
 * and sends the SPA back to the frontend with the access token.
 * This handler is only used when GoogleOAuth2Config is active.
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;
    private final RefreshCookieService refreshCookieService;
    private final String frontendUrl;

    public OAuth2LoginSuccessHandler(AuthService authService,
            RefreshCookieService refreshCookieService,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.authService = authService;
        this.refreshCookieService = refreshCookieService;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        if (!(authentication.getPrincipal() instanceof OAuth2User user)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Object email = user.getAttributes().get("email");
        Object googleId = user.getAttributes().get("sub");
        Object name = user.getAttributes().get("name");

        AuthResult result = authService.loginWithGoogle(
                email == null ? null : email.toString(),
                googleId == null ? null : googleId.toString(),
                name == null ? null : name.toString());

        refreshCookieService.set(response, result.tokens().refreshToken());

        // Redirect to the SPA callback page with NO token in the URL.
        // The callback page calls POST /auth/refresh to obtain the access token,
        // just like a page reload would. This avoids leaking the token via
        // browser history, Referer headers, and server logs.
        String redirect = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/auth/callback")
                .build()
                .encode()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirect);
    }
}
