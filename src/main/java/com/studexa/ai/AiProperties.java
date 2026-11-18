package com.studexa.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String apiKey,
        String model,
        int dailyLimit,
        int maxSourceChars,
        Duration timeout,
        String geminiApiKey,
        String geminiModel,
        boolean geminiEnabled) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean geminiConfigured() {
        return geminiEnabled && geminiApiKey != null && !geminiApiKey.isBlank();
    }
}
