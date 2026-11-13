package com.studexa.user;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.user.dto.MeResponse;
import com.studexa.user.dto.ProfileResponse;
import com.studexa.user.dto.UpdateMeRequest;
import com.studexa.user.dto.UserResponse;

/**
 * Reads and updates the logged-in user's own profile.
 */
@Service
public class MeService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    public MeService(UserRepository userRepository, ProfileRepository profileRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public MeResponse get(UUID userId) {
        return toResponse(requireUser(userId), requireProfile(userId));
    }

    @Transactional
    public MeResponse update(UUID userId, UpdateMeRequest request) {
        User user = requireUser(userId);
        Profile profile = requireProfile(userId);

        if (request.nickname() != null) {
            if (request.nickname().isBlank()) {
                throw ApiException.badRequest("INVALID_NICKNAME", "Nickname cannot be empty.");
            }
            profile.setNickname(request.nickname().trim());
        }
        if (request.avatar() != null) {
            profile.setAvatar(request.avatar().isBlank() ? null : request.avatar().trim());
        }
        if (request.language() != null) {
            profile.setLanguage(request.language());
        }
        if (request.levelOfStudy() != null) {
            profile.setLevelOfStudy(request.levelOfStudy().isBlank() ? null : request.levelOfStudy().trim());
        }
        if (request.timezone() != null) {
            profile.setTimezone(validTimezone(request.timezone()));
        }
        if (request.dailyGoalCards() != null) {
            profile.setDailyGoalCards(request.dailyGoalCards());
        }
        if (request.dailyGoalMinutes() != null) {
            profile.setDailyGoalMinutes(request.dailyGoalMinutes());
        }

        profileRepository.save(profile);
        return toResponse(user, profile);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Profile not found."));
    }

    private Profile requireProfile(UUID userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Profile not found."));
    }

    private static String validTimezone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException ex) {
            throw ApiException.badRequest("INVALID_TIMEZONE", "Unknown timezone: " + timezone);
        }
    }

    private static MeResponse toResponse(User user, Profile profile) {
        return new MeResponse(UserResponse.from(user), ProfileResponse.from(profile));
    }
}
