package com.studexa.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 320, message = "Email is too long.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.")
        String password,

        @Size(min = 1, max = 50, message = "Nickname must be 1 to 50 characters.")
        String nickname,

        @Pattern(regexp = "en|km", message = "Language must be en or km.")
        String language,

        @Size(max = 128)
        String inviteCode,

        Boolean adultConfirmed) {
}
