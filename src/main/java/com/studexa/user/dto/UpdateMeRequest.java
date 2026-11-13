package com.studexa.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial profile update. Null fields are left unchanged.
 */
public record UpdateMeRequest(
        @Size(min = 1, max = 50, message = "Nickname must be 1 to 50 characters.") String nickname,
        @Size(max = 100, message = "Avatar must be at most 100 characters.") String avatar,
        @Pattern(regexp = "en|km", message = "Language must be en or km.") String language,
        @Size(max = 50, message = "Level of study must be at most 50 characters.") String levelOfStudy,
        @Size(max = 64, message = "Timezone must be at most 64 characters.") String timezone,
        @Min(value = 1, message = "Daily card goal must be at least 1.")
        @Max(value = 500, message = "Daily card goal must be at most 500.")
        Integer dailyGoalCards,
        @Min(value = 1, message = "Daily minutes goal must be at least 1.")
        @Max(value = 600, message = "Daily minutes goal must be at most 600.")
        Integer dailyGoalMinutes) {
}
