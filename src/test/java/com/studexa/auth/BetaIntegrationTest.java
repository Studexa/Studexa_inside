package com.studexa.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
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
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.studexa.ai.GeminiClient;
import com.studexa.library.WikisourceReadings;

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
    @MockitoSpyBean private WikisourceReadings readings;

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
        send("/api/v1/ai/generate", auth, """
                {"task":"FLASHCARDS","basicOnly":true,"sourceText":"private attachment"}
                """).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/ai/source-limit").header("Authorization", auth)).andExpect(status().isNotFound());
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
    void betaBuildsBookStudyPromptsFromTheApprovedPassage() throws Exception {
        String auth = authHeader();
        var edition = readings.list().getFirst();
        String path = "/api/v1/library/readings/" + edition.id();
        doReturn(new WikisourceReadings.Reading(edition, List.of(
                new WikisourceReadings.Block(false, "Omitted 🧬 First paragraph."),
                new WikisourceReadings.Block(false, "Second paragraph. Omitted ending."))))
                .when(readings).get(edition.id());
        String payload = """
                {"revision":%d,"startBlock":0,"startOffset":11,"endBlock":1,"endOffset":17,
                 "language":"en","action":"SUMMARIZE"}
                """.formatted(edition.revision());
        mvc.perform(get("/api/v1/library/readings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/library/readings").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(9));
        mvc.perform(get("/explore/read/" + edition.id())).andExpect(forwardedUrl("/index.html"));
        send(path + "/ask", null, payload).andExpect(status().isUnauthorized());
        send("/api/v1/library/readings/outside-catalog/ask", auth, payload).andExpect(status().isNotFound());
        send(path + "/ask", auth, payload.replace("SUMMARIZE", "custom")).andExpect(status().isBadRequest());
        send(path + "/ask", auth, payload.replace("11", "9")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_READING_SELECTION"));
        send(path + "/ask", auth, payload.replace(Long.toString(edition.revision()), "1"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READING_CHANGED"));
        verifyNoInteractions(gemini);

        var sentPrompt = org.mockito.ArgumentCaptor.forClass(String.class);
        when(gemini.complete(anyString(), anyString())).thenReturn("""
                {"answer":"A summary.","references":[{"excerpt":"Second paragraph."}]}
                """);
        String withExtraFields = payload.replace("}", ",\"question\":\"private prompt\",\"sourceText\":\"private note\"}");
        send(path + "/ask", auth, withExtraFields).andExpect(status().isOk())
                .andExpect(jsonPath("$.references[0].excerpt").value("Second paragraph."));
        verify(gemini).complete(anyString(), sentPrompt.capture());
        assertThat(sentPrompt.getValue()).contains("Summarize the main ideas", "First paragraph.\n\nSecond paragraph.")
                .doesNotContain("private prompt", "private note", "Omitted");
    }

    @Test
    void betaRequiresSignInForLiveRoomApisAndServesRoomLinks() throws Exception {
        String auth = authHeader();
        send("/api/v1/auth/forgot-password", null, """
                {"email":"student@example.test"}
                """)
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/quizzes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/quizzes").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/quiz")).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/quiz/lobby/AA2345")).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/study/decks/" + UUID.randomUUID())).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/notes/" + UUID.randomUUID())).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void liveRoomStateRequiresMembershipAndOnlyHostCanAdvance() throws Exception {
        String host = authHeader();
        String player = authHeader();
        String outsider = authHeader();
        String quizId = json(send("/api/v1/quizzes", host, "{\"title\":\"Live water quiz\"}")
                .andExpect(status().isCreated()), "$.id");
        String questionId = json(send("/api/v1/quizzes/" + quizId + "/questions", host, """
                {"text":"What forms in the sky?","type":"MULTIPLE_CHOICE",
                 "options":"[\\"Clouds\\",\\"Sand\\"]","correctAnswer":"Clouds"}
                """).andExpect(status().isCreated()), "$.id");
        var created = send("/api/v1/sessions", host, """
                {"quizId":"%s","numQuestions":1}
                """.formatted(quizId)).andExpect(status().isCreated());
        String room = "/api/v1/sessions/" + json(created, "$.id");
        String code = json(created, "$.roomCode");
        String playerId = json(send(room + "/players", player, """
                {"roomCode":"%s","nickname":"Player"}
                """.formatted(code)).andExpect(status().isCreated()), "$.id");
        send(room + "/start", player, "{}").andExpect(status().isNotFound());
        send(room + "/start", host, "{}").andExpect(status().isOk());
        mvc.perform(get(room + "/state").header("Authorization", outsider)).andExpect(status().isNotFound());
        mvc.perform(get(room + "/leaderboard").header("Authorization", outsider)).andExpect(status().isNotFound());
        send(room + "/players", outsider, """
                {"roomCode":"%s","nickname":"Late player"}
                """.formatted(code)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROOM_CLOSED"));
        var snapshot = mvc.perform(get(room + "/state").header("Authorization", player)).andExpect(status().isOk())
                .andExpect(jsonPath("$.question.id").value(questionId))
                .andExpect(jsonPath("$.question.correctAnswer").doesNotExist())
                .andExpect(jsonPath("$.answer").doesNotExist());
        String progress = "{\"questionId\":\"" + questionId + "\"}";
        send(room + "/advance", player, progress).andExpect(status().isNotFound());
        send(room + "/advance", host, progress).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_IN_PROGRESS"));
        send("/api/v1/answers", player, """
                {"sessionPlayerId":"%s","questionId":"%s","selectedAnswer":"Clouds"}
                """.formatted(playerId, questionId)).andExpect(status().isCreated());
        mvc.perform(get(room + "/state").header("Authorization", player)).andExpect(status().isOk())
                .andExpect(jsonPath("$.question.endsAt").value(JsonPath.<Object>read(
                        snapshot.andReturn().getResponse().getContentAsString(), "$.question.endsAt")))
                .andExpect(jsonPath("$.answer.isCorrect").value(true));
    }

    private static String json(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
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
