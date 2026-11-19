package com.studexa.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves {@code index.html} for any client-side route so React Router can
 * handle navigation (e.g. direct deep-links to {@code /decks/123}).
 * <p>
 * API paths ({@code /api/**}), actuator, OpenAPI/Swagger, and OAuth2 paths
 * are intentionally not matched here so they fall through to their normal
 * handlers (404 if unmapped, handled by Spring Security otherwise).
 */
@Controller
public class SpaFallbackController {

    @GetMapping({"/login", "/register", "/auth/callback", "/demo", "/onboarding",
            "/study", "/study/**", "/subjects", "/notes", "/notes/{noteId}",
            "/plan", "/stats", "/profile", "/explore", "/explore/read/{id}", "/settings", "/design", "/library",
            "/quiz", "/quiz/create", "/quiz/join", "/quiz/lobby/{code}", "/quiz/play/{sessionId}",
            "/quiz/results/{sessionId}"})
    public String spaFallback() {
        return "forward:/index.html";
    }
}
