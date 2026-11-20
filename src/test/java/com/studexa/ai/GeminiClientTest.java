package com.studexa.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.SocketTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.studexa.common.ApiException;
import tools.jackson.databind.json.JsonMapper;

class GeminiClientTest {
    private static final String ORIGIN = "https://generativelanguage.googleapis.com/v1beta";
    private static final String MODEL = "gemini-3.5-flash-lite";
    private MockRestServiceServer server;
    private GeminiClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl(ORIGIN);
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new AiProperties(null, null, 10, 12000, Duration.ofSeconds(5),
                "test-key", MODEL, true);
        client = new GeminiClient(properties, JsonMapper.builder().build(), builder.build());
    }

    @ParameterizedTest
    @CsvSource({
            "UNAUTHORIZED, SERVICE_UNAVAILABLE, AI_PROVIDER_ACCESS_DENIED",
            "FORBIDDEN, SERVICE_UNAVAILABLE, AI_PROVIDER_ACCESS_DENIED",
            "TOO_MANY_REQUESTS, TOO_MANY_REQUESTS, AI_PROVIDER_LIMIT",
            "SERVICE_UNAVAILABLE, BAD_GATEWAY, AI_ERROR"
    })
    void translatesProviderErrors(HttpStatus providerStatus, HttpStatus status, String code) {
        server.expect(requestTo(ORIGIN + "/models/" + MODEL + ":generateContent"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andRespond(withStatus(providerStatus).body("Details from provider"));
        assertThatThrownBy(() -> client.complete("System", "Study question"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(status);
                    assertThat(error.getCode()).isEqualTo(code);
                    assertThat(error.getMessage()).doesNotContain("Details from provider", "test-key");
                });
        server.verify();
    }

    @Test
    void reportsTimeout() {
        server.expect(requestTo(ORIGIN + "/models/" + MODEL + ":generateContent"))
                .andRespond(withException(new SocketTimeoutException("Provider timeout")));
        assertThatThrownBy(() -> client.complete("System", "Study question"))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo("AI_TIMEOUT"));
        server.verify();
    }

    @Test
    void hidesMalformedProviderResponse() {
        server.expect(requestTo(ORIGIN + "/models/" + MODEL + ":generateContent"))
                .andRespond(withSuccess("Private provider response", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.complete("System", "Study question"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("AI_INVALID_RESPONSE");
                    assertThat(error.getMessage()).doesNotContain("Private provider response");
                });
        server.verify();
    }
}
