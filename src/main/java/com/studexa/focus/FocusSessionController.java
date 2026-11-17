package com.studexa.focus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.common.CurrentUser;

import jakarta.validation.Valid;

/**
 * Focus (pomodoro/timer) session management. Sessions belong to the
 * authenticated user and support range queries for dashboard/stats.
 */
@RestController
@RequestMapping("/api/v1/focus-sessions")
public class FocusSessionController {

    private final FocusSessionService focusSessionService;

    public FocusSessionController(FocusSessionService focusSessionService) {
        this.focusSessionService = focusSessionService;
    }

    @GetMapping
    public List<FocusSessionDto> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Instant start,
            @RequestParam(required = false) Instant end) {
        UUID userId = CurrentUser.id(jwt);
        if (start != null && end != null) {
            return focusSessionService.listByRange(userId, start, end);
        }
        return focusSessionService.list(userId);
    }

    @GetMapping("/{id}")
    public FocusSessionDto get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return focusSessionService.get(CurrentUser.id(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FocusSessionDto create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FocusSessionRequest request) {
        return focusSessionService.create(CurrentUser.id(jwt), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        focusSessionService.delete(CurrentUser.id(jwt), id);
    }

    @GetMapping("/count")
    public Long countInRange(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam Instant start,
            @RequestParam Instant end) {
        return focusSessionService.countInRange(CurrentUser.id(jwt), start, end);
    }
}