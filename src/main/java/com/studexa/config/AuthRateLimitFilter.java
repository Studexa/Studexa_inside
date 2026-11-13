package com.studexa.config;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.studexa.common.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Small in-memory fixed-window limiter for sensitive auth endpoints.
 * For a single backend instance this is enough; a shared store (Redis)
 * would be needed when running multiple instances.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password");

    private final boolean enabled;
    private final int maxRequests;
    private final long windowMillis;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(
            @Value("${app.security.auth-rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.auth-rate-limit.max-requests:30}") int maxRequests,
            @Value("${app.security.auth-rate-limit.window-seconds:60}") long windowSeconds) {
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || !LIMITED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getRemoteAddr() + ':' + request.getRequestURI();
        if (!tryConsume(key)) {
            response.setHeader("Retry-After", String.valueOf(Math.max(1, windowMillis / 1000)));
            ErrorResponseWriter.write(response, 429, "TOO_MANY_REQUESTS",
                    "Too many attempts. Please wait a moment and try again.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean tryConsume(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
        synchronized (window) {
            if (now - window.startedAt >= windowMillis) {
                window.startedAt = now;
                window.count = 0;
            }
            if (window.count >= maxRequests) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    private static final class Window {
        private long startedAt;
        private int count;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
