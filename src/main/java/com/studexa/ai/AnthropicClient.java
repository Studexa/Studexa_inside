package com.studexa.ai;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.studexa.common.ApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AnthropicClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
    private static final String API_VERSION = "2023-06-01";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AnthropicClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!properties.configured()) {
            throw ApiException.serviceUnavailable(
                    "AI_NOT_CONFIGURED",
                    "AI is not configured yet. Add ANTHROPIC_API_KEY to the backend environment.");
        }

        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "max_tokens", 2400,
                "temperature", 0.4,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userPrompt)));

        try {
            String raw = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", properties.apiKey())
                    .header("anthropic-version", API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (raw == null || raw.isBlank()) {
                throw ApiException.badGateway("AI_ERROR", "The AI returned an empty response.");
            }
            JsonNode response = objectMapper.readTree(raw);
            StringBuilder text = new StringBuilder();
            for (JsonNode block : response.path("content")) {
                if ("text".equals(block.path("type").asString())) {
                    text.append(block.path("text").asString());
                }
            }
            if (text.isEmpty()) {
                throw ApiException.badGateway("AI_ERROR", "The AI returned no text.");
            }
            return text.toString();
        } catch (ApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.warn("Anthropic request failed with status {}", ex.getStatusCode().value());
            String message = ex.getStatusCode().value() == 429
                    ? "AI is busy right now. Please try again in a moment."
                    : "The AI service could not complete that request. Please try again.";
            throw ApiException.badGateway("AI_ERROR", message);
        } catch (ResourceAccessException ex) {
            log.warn("Anthropic request timed out or could not connect", ex);
            throw ApiException.badGateway("AI_TIMEOUT", "The AI took too long to answer. Please try again.");
        }
    }
}
