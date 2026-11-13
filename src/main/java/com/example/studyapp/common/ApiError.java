package com.example.studyapp.common;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Consistent JSON error body used by controllers and the security layer.
 * Fields is only serialized for validation errors.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(int status, String code, String message, Map<String, String> fields) {

    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, null);
    }

    public static ApiError validation(int status, String message, Map<String, String> fields) {
        return new ApiError(status, "VALIDATION_ERROR", message, fields);
    }
}
