package com.studexa.game;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.deck.ReviewLogRepository;
import com.studexa.game.dto.PetDto;
import com.studexa.user.ProfileRepository;

/**
 * Study Buddy service. Manages the pet's mood, stage, accessories, and naming.
 * Mood follows whether the student studied today (in their timezone).
 * Stage follows the student's level.
 */
@Service
public class PetService {

    private final PetRepository petRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final ProfileRepository profileRepository;
    private final GameStateRepository gameStateRepository;
    private final Clock clock;

    public PetService(PetRepository petRepository,
            ReviewLogRepository reviewLogRepository, ProfileRepository profileRepository,
            GameStateRepository gameStateRepository, Clock clock) {
        this.petRepository = petRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.profileRepository = profileRepository;
        this.gameStateRepository = gameStateRepository;
        this.clock = clock;
    }

    /**
     * Get or create the student's pet. Mood is computed from study activity.
     */
    @Transactional
    public PetDto getPet(UUID userId) {
        petRepository.findById(userId).orElseGet(() -> createPet(userId));
        // Keep the values returned by GET current; clients should not need to
        // know about separate maintenance endpoints for derived pet state.
        updateMood(userId);
        updateStage(userId);
        Pet pet = petRepository.findById(userId).orElseThrow();
        return toDto(pet);
    }

    /**
     * Update the pet's mood. Derived server-side from whether the student
     * reviewed any cards today (in their timezone) and their current streak.
     */
    @Transactional
    public void updateMood(UUID userId) {
        Pet pet = petRepository.findById(userId)
                .orElseGet(() -> createPet(userId));
        GameState state = gameStateRepository.findById(userId).orElse(null);
        int streak = state != null ? state.getStreakCurrent() : 0;

        // Derive studiedToday from review logs, not from the client.
        ZoneId zoneId = zoneIdFor(userId);
        LocalDate today = clock.instant().atZone(zoneId).toLocalDate();
        Instant dayStart = today.atStartOfDay(zoneId).toInstant();
        Instant nextDayStart = today.plusDays(1).atStartOfDay(zoneId).toInstant();
        boolean studiedToday = reviewLogRepository
                .existsByUserIdAndReviewedAtGreaterThanEqualAndReviewedAtLessThan(
                        userId, dayStart, nextDayStart);

        String mood = Pet.moodFor(studiedToday, streak);
        if (!mood.equals(pet.getMood())) {
            pet.setMood(mood);
            pet.setUpdatedAt(clock.instant());
            petRepository.save(pet);
        }
    }

    /**
     * Update the pet's stage. Derived server-side from the student's level
     * in game_state.
     */
    @Transactional
    public void updateStage(UUID userId) {
        Pet pet = petRepository.findById(userId)
                .orElseGet(() -> createPet(userId));
        GameState state = gameStateRepository.findById(userId).orElse(null);
        int level = state != null ? state.getLevel() : 1;
        String stage = Pet.stageForLevel(level);
        if (!stage.equals(pet.getStage())) {
            pet.setStage(stage);
            pet.setUpdatedAt(clock.instant());
            petRepository.save(pet);
        }
    }

    /**
     * Rename the pet.
     */
    @Transactional
    public void renamePet(UUID userId, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw ApiException.badRequest("INVALID_NAME", "Pet name cannot be empty.");
        }
        if (name.trim().length() > 50) {
            throw ApiException.badRequest("INVALID_NAME", "Pet name must be 50 characters or fewer.");
        }
        Pet pet = petRepository.findById(userId)
                .orElseGet(() -> createPet(userId));
        pet.setName(name.trim());
        pet.setUpdatedAt(clock.instant());
        petRepository.save(pet);
    }

    /**
     * Equip or unequip an accessory.
     */
    @Transactional
    public void equipAccessory(UUID userId, String accessoryType) {
        Pet pet = petRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Pet not found."));
        String slot = Pet.accessorySlot(accessoryType);
        if (slot == null) {
            pet.setEquippedAccessory(null);
        } else {
            // Verify the user owns this accessory.
            // Ownership check is done in ShopService; here we just set it.
            pet.setEquippedAccessory(slot);
        }
        pet.setUpdatedAt(clock.instant());
        petRepository.save(pet);
    }

    private Pet createPet(UUID userId) {
        // Default species is owl; could be chosen during onboarding.
        Pet pet = new Pet(userId, "owl");
        return petRepository.save(pet);
    }

    private PetDto toDto(Pet pet) {
        return new PetDto(
                pet.getUserId(), pet.getSpecies(), pet.getName(), pet.getStage(),
                pet.getMood(), pet.getAccessories(), pet.getEquippedAccessory(),
                pet.getCreatedAt(), pet.getUpdatedAt());
    }

    private ZoneId zoneIdFor(UUID userId) {
        return profileRepository.findById(userId)
                .map(p -> {
                    try {
                        return ZoneId.of(p.getTimezone());
                    } catch (Exception e) {
                        return ZoneId.of("Asia/Phnom_Penh");
                    }
                })
                .orElse(ZoneId.of("Asia/Phnom_Penh"));
    }
}
