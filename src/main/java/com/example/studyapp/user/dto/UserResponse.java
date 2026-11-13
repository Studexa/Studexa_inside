package com.example.studyapp.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.example.studyapp.user.User;

public record UserResponse(UUID id, String email, String role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name(), user.getCreatedAt());
    }
}
