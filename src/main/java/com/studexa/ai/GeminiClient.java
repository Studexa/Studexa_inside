package com.studexa.ai;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.studexa.common.ApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Calls Google's Gemini API (generativelanguage.googleapis.com).
 * Enabled when {@code app.ai.gemini.enabled=true} (default: false so
 * Anthropic remains the default unless explicitly switched).
 */
@Component
@Primary
public class GeminiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!properties.geminiConfigured()) {
            throw ApiException.serviceUnavailable(
                    "AI_NOT_CONFIGURED",
                    "AI is not configured yet. Add GEMINI_API_KEY to the backend environment.");
        }

        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.4,
                        "responseMimeType", "application/json",
                        "maxOutputTokens", 2400));

        String model = properties.geminiModel();
        if (model == null || !model.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
            throw ApiException.serviceUnavailable("AI_NOT_CONFIGURED", "The Gemini model is not configured.");
        }

        try {
            String raw = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", properties.geminiApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (raw == null || raw.isBlank()) {
                throw ApiException.badGateway("AI_ERROR", "The AI returned an empty response.");
            }
            JsonNode response = objectMapper.readTree(raw);
            JsonNode candidates = response.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.path(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    StringBuilder text = new StringBuilder();
                    for (JsonNode part : parts) {
                        if (!part.path("thought").asBoolean(false) && part.path("text").isString()) {
                            text.append(part.path("text").asString());
                        }
                    }
                    if (!text.toString().isBlank()) {
                        return text.toString();
                    }
                }
            }
            throw ApiException.badGateway("AI_ERROR", "The AI returned no text.");
        } catch (ApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.warn("Gemini request failed with status {}", ex.getStatusCode().value());
            if (ex.getStatusCode().value() == 429) {
                throw ApiException.tooManyRequests("AI_PROVIDER_LIMIT", "Gemini is busy. Please try again later.");
            }
            throw ApiException.badGateway("AI_ERROR", "The AI service could not complete that request. Please try again.");
        } catch (ResourceAccessException ex) {
            log.warn("Gemini request timed out or could not connect");
            throw ApiException.badGateway("AI_TIMEOUT", "The AI took too long to answer. Please try again.");
        }
    }
}
