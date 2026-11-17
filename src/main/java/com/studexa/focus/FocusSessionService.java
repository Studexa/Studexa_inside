package com.studexa.focus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.user.ProfileRepository;

/**
 * Server-backed focus session service. Records completed pomodoro/timer
 * sessions so XP and streak data sync across devices and can be verified
 * server-side.
 */
@Service
public class FocusSessionService {

    private final FocusSessionRepository focusSessionRepository;
    private final ProfileRepository profileRepository;

    public FocusSessionService(FocusSessionRepository focusSessionRepository, ProfileRepository profileRepository) {
        this.focusSessionRepository = focusSessionRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public List<FocusSessionDto> list(UUID userId) {
        return focusSessionRepository.findByUserIdOrderByCompletedAtDesc(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FocusSessionDto> listByRange(UUID userId, Instant start, Instant end) {
        return focusSessionRepository.findByUserIdAndCompletedAtBetween(userId, start, end).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FocusSessionDto get(UUID userId, UUID id) {
        FocusSession session = focusSessionRepository.findById(id)
                .filter(f -> f.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Focus session not found."));
        return toDto(session);
    }

    @Transactional
    public FocusSessionDto create(UUID userId, FocusSessionRequest request) {
        profileRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Profile not found."));

        Instant now = Instant.now();
        // completedAt must not be in the future
        if (request.completedAt().isAfter(now)) {
            throw ApiException.badRequest("FUTURE_SESSION", "Completed-at cannot be in the future.");
        }

        FocusSession session = FocusSession.create(userId, request.minutes(), request.completedAt());
        session = focusSessionRepository.save(session);
        return toDto(session);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        boolean exists = focusSessionRepository.findById(id)
                .map(f -> f.getUserId().equals(userId))
                .orElse(false);
        if (!exists) {
            throw ApiException.notFound("Focus session not found.");
        }
        focusSessionRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Long countInRange(UUID userId, Instant start, Instant end) {
        return focusSessionRepository.countByUserIdAndCompletedAtBetween(userId, start, end);
    }

    private FocusSessionDto toDto(FocusSession session) {
        return new FocusSessionDto(
                session.getId(),
                session.getMinutes(),
                session.getCompletedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }
}