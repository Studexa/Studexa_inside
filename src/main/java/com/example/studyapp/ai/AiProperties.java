package com.example.studyapp.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String apiKey,
        String model,
        int dailyLimit,
        int maxSourceChars,
        Duration timeout) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
