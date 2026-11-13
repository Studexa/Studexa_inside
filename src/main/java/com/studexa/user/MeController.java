package com.studexa.user;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.common.ApiException;
import com.studexa.user.dto.MeResponse;
import com.studexa.user.dto.UpdateMeRequest;

import jakarta.validation.Valid;

/**
 * The user id always comes from the access token, never from the request.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return meService.get(currentUserId(jwt));
    }

    @PutMapping
    public MeResponse update(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMeRequest request) {
        return meService.update(currentUserId(jwt), request);
    }

    private static UUID currentUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ex) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Authentication required.");
        }
    }
}
