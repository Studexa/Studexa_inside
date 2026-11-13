package com.studexa.subject;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.common.ApiException;
import com.studexa.subject.dto.CreateSubjectRequest;
import com.studexa.subject.dto.SubjectResponse;
import com.studexa.subject.dto.UpdateSubjectRequest;

import jakarta.validation.Valid;

/**
 * The user id always comes from the access token, never from the request.
 */
@RestController
@RequestMapping("/api/v1/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    public List<SubjectResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Boolean archived) {
        return subjectService.list(currentUserId(jwt), archived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectResponse create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSubjectRequest request) {
        return subjectService.create(currentUserId(jwt), request);
    }

    @PutMapping("/{id}")
    public SubjectResponse update(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubjectRequest request) {
        return subjectService.update(currentUserId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        subjectService.delete(currentUserId(jwt), id);
    }

    private static UUID currentUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ex) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Authentication required.");
        }
    }
}
