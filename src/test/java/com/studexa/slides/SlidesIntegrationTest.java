package com.studexa.slides;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.studexa.ai.GeminiClient;

@SpringBootTest(properties = {
        "app.beta.enabled=false", "app.security.auth-rate-limit.enabled=false", "app.ai.gemini-enabled=true",
        "app.ai.gemini-api-key=test-only", "app.ai.daily-limit=2", "app.ai.max-source-chars=64"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SlidesIntegrationTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private GeminiClient gemini;

    @Test
    void extractsOnlyTheTextThatWillBeSentAndGeneratesFromFileOrPrompt() throws Exception {
        String auth = register();
        String text = "Water evaporates and rises into the air. ".repeat(4);
        String preview = JsonPath.read(upload(auth, "notes.md", text.getBytes(UTF_8))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.truncated").value(true)).andReturn().getResponse().getContentAsString(), "$.text");
        assertThat(preview).isEqualTo(text.substring(0, 64).stripTrailing());
        verifyNoInteractions(gemini);

        when(gemini.complete(anyString(), anyString())).thenReturn(fixture());
        send(auth, "/api/v1/ai/slides/generate", """
                {"mode":"SOURCE","text":"%s","language":"en"}
                """.formatted(preview)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.slides.length()").value(5))
                .andExpect(jsonPath("$.content.questions.length()").value(4));
        verify(gemini).complete(anyString(), endsWith("Source material:\n" + preview));
        send(auth, "/api/v1/ai/slides/generate", prompt()).andExpect(status().isOk());
        verify(gemini).complete(anyString(), endsWith("Requested topic:\nExplain the water cycle"));
        send(auth, "/api/v1/ai/slides/generate", prompt()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_LIMIT_REACHED"));
        verify(gemini, times(2)).complete(anyString(), anyString());
    }

    @Test
    void rejectsInvalidFilesAndRefundsMalformedAiResponses() throws Exception {
        String auth = register();
        upload(auth, "notes.png", new byte[]{1}).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE"));
        upload(auth, "notes.txt", new byte[5 * 1024 * 1024 + 1]).andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        upload(auth, "notes.txt", " \n ".getBytes(UTF_8)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_READABLE_TEXT"));
        verifyNoInteractions(gemini);

        String fixture = fixture();
        when(gemini.complete(anyString(), anyString())).thenReturn("{\"slides\":[]}",
                fixture.replace("\"Sand\"", "\"sunlight\""), fixture);
        for (int attempt = 0; attempt < 2; attempt++) {
            send(auth, "/api/v1/ai/slides/generate", prompt()).andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("AI_INVALID_RESPONSE"));
        }
        send(auth, "/api/v1/ai/slides/generate", prompt()).andExpect(status().isOk());
        send(auth, "/api/v1/ai/slides/generate", prompt()).andExpect(status().isOk());
    }

    @Test
    void savesOnePrivateDeckAndQuizForConcurrentRequests() throws Exception {
        String owner = register();
        String outsider = register();
        String id = UUID.randomUUID().toString();
        String draft = "{\"id\":\"" + id + "\",\"content\":" + fixture() + "}";
        var first = CompletableFuture.supplyAsync(() -> save(owner, draft));
        var second = CompletableFuture.supplyAsync(() -> save(owner, draft));
        String quizId = first.get();
        assertThat(second.get()).isEqualTo(quizId);
        String deckPath = "/api/v1/slide-decks/" + id;
        String quizPath = "/api/v1/quizzes/" + quizId;

        mvc.perform(get(deckPath)).andExpect(status().isUnauthorized());
        mvc.perform(get(deckPath).header("Authorization", outsider)).andExpect(status().isNotFound());
        mvc.perform(get(quizPath).header("Authorization", outsider)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/slide-decks").header("Authorization", outsider))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/slide-decks").header("Authorization", owner))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].slides").doesNotExist());
        mvc.perform(get(deckPath).header("Authorization", owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.slides[4].title").value("Collection"))
                .andExpect(jsonPath("$.source").doesNotExist());
        mvc.perform(get("/api/v1/quizzes").header("Authorization", owner)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(quizPath).header("Authorization", owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.quiz.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.questions.length()").value(4))
                .andExpect(jsonPath("$.questions[0].correctAnswer").value("Sunlight"))
                .andExpect(jsonPath("$.questions[3].correctAnswer").value("Rivers"));
        send(owner, quizPath + "/questions", "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLIDE_QUIZ_READ_ONLY"));
        send(owner, "/api/v1/slide-decks", "{}").andExpect(status().isBadRequest());
        verifyNoInteractions(gemini);
    }

    private String save(String auth, String draft) {
        try {
            return JsonPath.read(send(auth, "/api/v1/slide-decks", draft).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(), "$.quizId");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ResultActions upload(String auth, String name, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/v1/ai/slides/extract").file(new MockMultipartFile("file", name, null, bytes))
                .header("Authorization", auth));
    }

    private ResultActions send(String auth, String path, String body) throws Exception {
        return mvc.perform(post(path).header("Authorization", auth).contentType("application/json").content(body));
    }

    private String register() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("""
                {"email":"%s@example.test","password":"test-password1","nickname":"Dara"}
                """.formatted(UUID.randomUUID()))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private static String fixture() throws Exception {
        return new ClassPathResource("slides-draft.json").getContentAsString(UTF_8);
    }

    private static String prompt() {
        return """
                {"mode":"PROMPT","text":"Explain the water cycle","language":"en"}
                """;
    }
}
