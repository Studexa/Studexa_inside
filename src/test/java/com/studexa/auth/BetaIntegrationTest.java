package com.studexa.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.studexa.ai.GeminiClient;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.beta.enabled=true",
        "app.ai.gemini-enabled=true",
        "app.ai.gemini-api-key=test-only",
        "app.security.auth-rate-limit.enabled=false"
})
class BetaIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private GeminiClient gemini;

    @Test
    void registrationRequiresAnUnexpiredUnrevokedInviteForAnAdultWithTheMatchingEmail() throws Exception {
        String email = email();
        String code = invite(email, 3600, false);
        register(email, code, false).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BETA_ADULT_REQUIRED"));
        register(email(), code, true).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BETA_INVALID_INVITE"));
        register(email, invite(email, -60, false), true).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BETA_INVALID_INVITE"));
        register(email, invite(email, 3600, true), true).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BETA_INVALID_INVITE"));
        register(email, code, true).andExpect(status().isCreated());
        register(email, code, true).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BETA_INVALID_INVITE"));
        String duplicateAccountInvite = invite(email, 3600, false);
        register(email, duplicateAccountInvite, true).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT redeemed_at IS NULL FROM beta_invites WHERE code_hash = ?",
                Boolean.class, TokenHasher.sha256(duplicateAccountInvite))).isTrue();
    }

    @Test
    void betaRejectsOpenPromptsAndBuildsGeminiRequestsFromTheCatalog() throws Exception {
        String auth = authHeader();
        mvc.perform(get("/api/v1/ai/samples")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ai/samples").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[1].id").value("water-cycle-km"));
        send("/api/v1/ai/tutor", auth, """
                {"question":"tester private question","sourceText":"private note"}
                """).andExpect(status().isNotFound());
        send("/api/v1/ai/generate", auth, """
                {"task":"QUIZ","deckId":"00000000-0000-0000-0000-000000000001","sourceText":"private note"}
                """).andExpect(status().isNotFound());
        send("/api/v1/ai/samples/tutor", auth, """
                {"sampleId":"water-cycle-en","promptId":"unknown"}
                """).andExpect(status().isBadRequest());
        verifyNoInteractions(gemini);

        when(gemini.complete(anyString(), anyString())).thenReturn("""
                {"answer":"Water vapor cools into droplets.",
                 "references":[{"excerpt":"it condenses into tiny liquid droplets that form clouds"}]}
                """);
        send("/api/v1/ai/samples/tutor", auth, """
                {"sampleId":"water-cycle-en","promptId":"explain"}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.references[0].title").value("The water cycle"));
        verify(gemini).complete(anyString(), contains("Explain how clouds form."));

        when(gemini.complete(anyString(), anyString())).thenReturn("""
                {"items":[{"type":"MULTIPLE_CHOICE","front":"តើពពកកើតពីអ្វី?",
                 "back":"ដំណក់ទឹក","choices":["ដំណក់ទឹក","ខ្សាច់","ថ្ម"]}]}
                """);
        send("/api/v1/ai/samples/generate", auth, """
                {"sampleId":"water-cycle-km","task":"QUIZ","count":1}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].choices.length()").value(3));
        verify(gemini).complete(anyString(), contains("វដ្តទឹក"));
    }

    @Test
    void betaKeepsLiveFeaturesClosedAndServesNestedStudyRoutes() throws Exception {
        String auth = authHeader();
        send("/api/v1/auth/forgot-password", null, """
                {"email":"student@example.test"}
                """)
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/quizzes").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(get("/quiz/info").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(get("/study/decks/" + UUID.randomUUID())).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/notes/" + UUID.randomUUID())).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void persistsExamImportanceAndQuestKindsInTheExistingDatabaseFormat() throws Exception {
        String auth = authHeader();
        send("/api/v1/exams", auth, """
                {"title":"Beta exam","examDate":"2030-05-20","importance":"HIGH"}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.importance").value("HIGH"));
        mvc.perform(get("/api/v1/exams").header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].examDate").value("2030-05-20"))
                .andExpect(jsonPath("$[0].importance").value("HIGH"));
        mvc.perform(get("/api/v1/quests/today").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].code").isNotEmpty());
    }

    private ResultActions register(String email, String code, boolean adult) throws Exception {
        return send("/api/v1/auth/register", null, """
                {"email":"%s","password":"test-password1","nickname":"Dara","inviteCode":"%s","adultConfirmed":%b}
                """.formatted(email, code, adult));
    }

    private ResultActions send(String path, String auth, String payload) throws Exception {
        var request = post(path).contentType("application/json").content(payload);
        if (auth != null) request.header("Authorization", auth);
        return mvc.perform(request);
    }

    private String authHeader() throws Exception {
        String email = email();
        String response = register(email, invite(email, 3600, false), true)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.<String>read(response, "$.accessToken");
    }

    private static String email() {
        return UUID.randomUUID() + "@example.test";
    }

    private String invite(String email, long expiresIn, boolean revoked) {
        String code = TokenHasher.newRawToken();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO beta_invites (code_hash, email, expires_at, revoked_at) VALUES (?, ?, ?, ?)",
                TokenHasher.sha256(code), email, Timestamp.from(now.plusSeconds(expiresIn)),
                revoked ? Timestamp.from(now) : null);
        return code;
    }
}
