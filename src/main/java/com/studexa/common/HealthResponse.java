package com.studexa.common;

/**
 * Response for the public health check.
 */
public record HealthResponse(String status, String db) {
}
