package com.studexa.boss;

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
 * Boss battle (exam-prep fight) management. Fights belong to the
 * authenticated user and support range queries for stats/dashboard.
 */
@RestController
@RequestMapping("/api/v1/boss-fights")
public class BossFightController {

    private final BossFightService bossFightService;

    public BossFightController(BossFightService bossFightService) {
        this.bossFightService = bossFightService;
    }

    @GetMapping
    public List<BossFightDto> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Instant start,
            @RequestParam(required = false) Instant end) {
        UUID userId = CurrentUser.id(jwt);
        if (start != null && end != null) {
            return bossFightService.listByRange(userId, start, end);
        }
        return bossFightService.list(userId);
    }

    @GetMapping("/{id}")
    public BossFightDto get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return bossFightService.get(CurrentUser.id(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BossFightDto create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BossFightRequest request) {
        return bossFightService.create(CurrentUser.id(jwt), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        bossFightService.delete(CurrentUser.id(jwt), id);
    }

    @GetMapping("/count")
    public Long countInRange(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam Instant start,
            @RequestParam Instant end) {
        return bossFightService.countInRange(CurrentUser.id(jwt), start, end);
    }

    @GetMapping("/wins/count")
    public Long countWins(
            @AuthenticationPrincipal Jwt jwt) {
        return bossFightService.countWins(CurrentUser.id(jwt));
    }
}