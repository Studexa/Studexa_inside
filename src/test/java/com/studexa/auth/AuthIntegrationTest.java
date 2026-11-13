package com.studexa.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.auth-rate-limit.enabled=false",
        "app.jwt.secret=integration-test-secret-0123456789abcdef"
})
class AuthIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void registerLoginRefreshLogoutFlow() throws Exception {
        Registered registered = register(uniqueEmail(), "password123", "Dara");

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(registered.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(registered.email()))
                .andExpect(jsonPath("$.profile.nickname").value("Dara"))
                .andExpect(jsonPath("$.profile.timezone").value("Asia/Phnom_Penh"));

        MvcResult refreshResult = mvc.perform(post("/api/v1/auth/refresh").cookie(registered.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(cookie().exists(RefreshCookieService.COOKIE_NAME))
                .andReturn();

        Cookie rotated = refreshResult.getResponse().getCookie(RefreshCookieService.COOKIE_NAME);
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(registered.refreshCookie().getValue());

        mvc.perform(post("/api/v1/auth/logout").cookie(rotated))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh").cookie(rotated))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "First");

        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, "password123", "Second")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email, "password123", "Dara");

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointRejectsMissingOrTamperedToken() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersOnlySeeTheirOwnProfile() throws Exception {
        Registered alice = register(uniqueEmail(), "password123", "Alice");
        Registered bob = register(uniqueEmail(), "password123", "Bob");

        mvc.perform(put("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(alice.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"Alice Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.nickname").value("Alice Updated"));

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(alice.accessToken())))
                .andExpect(jsonPath("$.user.email").value(alice.email()));

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(bob.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(bob.email()))
                .andExpect(jsonPath("$.profile.nickname").value("Bob"));
    }

    @Test
    void reusingAnOldRefreshTokenIsRejected() throws Exception {
        Registered registered = register(uniqueEmail(), "password123", "Dara");

        mvc.perform(post("/api/v1/auth/refresh").cookie(registered.refreshCookie()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/refresh").cookie(registered.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void profileUpdateValidatesTimezoneAndGoals() throws Exception {
        Registered registered = register(uniqueEmail(), "password123", "Dara");

        mvc.perform(put("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(registered.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"Mars/Olympus\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));

        mvc.perform(put("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(registered.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dailyGoalCards\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void forgotPasswordDoesNotRevealWhetherAnAccountExists() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody-%s@example.com\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isAccepted());

        String email = uniqueEmail();
        register(email, "password123", "Dara");

        mvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());
    }

    @Test
    void resetPasswordWithUnknownTokenIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"unknown-token\",\"newPassword\":\"newpassword123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void registerValidatesInput() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    private Registered register(String email, String password, String nickname) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, password, nickname)))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists(RefreshCookieService.COOKIE_NAME))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");
        Cookie refreshCookie = result.getResponse().getCookie(RefreshCookieService.COOKIE_NAME);
        assertThat(refreshCookie).isNotNull();
        return new Registered(email, accessToken, refreshCookie);
    }

    private static String registerBody(String email, String password, String nickname) {
        return "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"%s\"}"
                .formatted(email, password, nickname);
    }

    private static String uniqueEmail() {
        return "student-" + UUID.randomUUID() + "@example.com";
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record Registered(String email, String accessToken, Cookie refreshCookie) {
    }
}
