package com.studexa.boss;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.user.ProfileRepository;

/**
 * Server-backed boss fight service. Records 5-question exam-prep battle
 * results so XP awards and badge progress sync across devices and can be
 * verified server-side.
 */
@Service
public class BossFightService {

    private final BossFightRepository bossFightRepository;
    private final ProfileRepository profileRepository;

    public BossFightService(BossFightRepository bossFightRepository, ProfileRepository profileRepository) {
        this.bossFightRepository = bossFightRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public List<BossFightDto> list(UUID userId) {
        return bossFightRepository.findByUserIdOrderByCompletedAtDesc(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BossFightDto> listByRange(UUID userId, Instant start, Instant end) {
        return bossFightRepository.findByUserIdAndCompletedAtBetween(userId, start, end).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BossFightDto get(UUID userId, UUID id) {
        BossFight fight = bossFightRepository.findById(id)
                .filter(b -> b.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Boss fight not found."));
        return toDto(fight);
    }

    @Transactional
    public BossFightDto create(UUID userId, BossFightRequest request) {
        profileRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Profile not found."));

        Instant now = Instant.now();
        if (request.completedAt().isAfter(now)) {
            throw ApiException.badRequest("FUTURE_FIGHT", "Completed-at cannot be in the future.");
        }

        BossFight fight = BossFight.create(
                userId,
                request.won(),
                request.questionsCount(),
                request.heartsRemaining(),
                request.completedAt());
        fight = bossFightRepository.save(fight);
        return toDto(fight);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        boolean exists = bossFightRepository.findById(id)
                .map(b -> b.getUserId().equals(userId))
                .orElse(false);
        if (!exists) {
            throw ApiException.notFound("Boss fight not found.");
        }
        bossFightRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Long countWins(UUID userId) {
        return bossFightRepository.countByUserIdAndWonTrue(userId);
    }

    @Transactional(readOnly = true)
    public Long countInRange(UUID userId, Instant start, Instant end) {
        return bossFightRepository.countByUserIdAndCompletedAtBetween(userId, start, end);
    }

    private BossFightDto toDto(BossFight fight) {
        return new BossFightDto(
                fight.getId(),
                fight.getWon(),
                fight.getQuestionsCount(),
                fight.getHeartsRemaining(),
                fight.getCompletedAt(),
                fight.getCreatedAt(),
                fight.getUpdatedAt());
    }
}