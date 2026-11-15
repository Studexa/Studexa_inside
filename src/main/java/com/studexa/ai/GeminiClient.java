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
        String model = properties.geminiModel() != null ? properties.geminiModel() : "gemini-2.0-flash-exp";
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
                        "maxOutputTokens", 2400));

        String model = properties.geminiModel() != null ? properties.geminiModel() : "gemini-2.0-flash-exp";

        try {
            String raw = restClient.post()
                    .uri("/models/" + model + ":generateContent?key=" + properties.geminiApiKey())
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
                    String text = parts.path(0).path("text").asText();
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
            throw ApiException.badGateway("AI_ERROR", "The AI returned no text.");
        } catch (ApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.warn("Gemini request failed with status {}", ex.getStatusCode().value());
            String message = ex.getStatusCode().value() == 429
                    ? "AI is busy right now. Please try again in a moment."
                    : "The AI service could not complete that request. Please try again.";
            throw ApiException.badGateway("AI_ERROR", message);
        } catch (ResourceAccessException ex) {
            log.warn("Gemini request timed out or could not connect", ex);
            throw ApiException.badGateway("AI_TIMEOUT", "The AI took too long to answer. Please try again.");
        }
    }
}
