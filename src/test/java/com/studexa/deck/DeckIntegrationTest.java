package com.studexa.deck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.auth-rate-limit.enabled=false",
        "app.jwt.secret=integration-test-secret-0123456789abcdef",
        "app.ai.api-key=",
        // .env is imported via spring.config.import and may contain a real
        // GEMINI_API_KEY on dev machines; blank these so the "AI not
        // configured" scenario is deterministic in any environment.
        "app.ai.gemini-api-key=",
        "app.ai.gemini-enabled=false"
})
class DeckIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void deckCardsDueAndReviewFlow() throws Exception {
        String token = registerAndGetToken();
        String deckId = createDeck(token);

        MvcResult first = mvc.perform(post("/api/v1/decks/{id}/cards", deckId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"BASIC","front":"What is 2 + 2?","back":"4",
                         "sourceTitle":"Arithmetic notes","sourceExcerpt":"Two plus two equals four."}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.front").value("What is 2 + 2?"))
                .andExpect(jsonPath("$.due").value(true))
                .andReturn();
        String firstCardId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/decks/{id}/cards/bulk", deckId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"cards":[
                          {"type":"BASIC","front":"What is 3 + 3?","back":"6"},
                          {"type":"BASIC","front":"What is 4 + 4?","back":"8"}
                        ]}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(get("/api/v1/decks").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cardCount").value(3))
                .andExpect(jsonPath("$[0].dueCount").value(3));

        mvc.perform(get("/api/v1/cards/due").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].deckTitle").value("Algebra warm-ups"))
                .andExpect(jsonPath("$[0].sourceTitle").value("Arithmetic notes"));

        mvc.perform(post("/api/v1/review/answer")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\":\"" + firstCardId + "\",\"rating\":\"GOOD\",\"msTaken\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REVIEW"))
                .andExpect(jsonPath("$.xpEarned").value(5));

        mvc.perform(get("/api/v1/cards/due").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(get("/api/v1/decks").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dueCount").value(2));
    }

    @Test
    void anotherUserCannotReviewSomeoneElsesCard() throws Exception {
        String alice = registerAndGetToken();
        String bob = registerAndGetToken();
        String deckId = createDeck(alice);

        MvcResult created = mvc.perform(post("/api/v1/decks/{id}/cards", deckId)
                .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"BASIC\",\"front\":\"Front\",\"back\":\"Back\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String cardId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/review/answer")
                .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\":\"" + cardId + "\",\"rating\":\"EASY\",\"msTaken\":1000}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void reviewRejectsUnknownRating() throws Exception {
        String token = registerAndGetToken();
        String deckId = createDeck(token);
        MvcResult created = mvc.perform(post("/api/v1/decks/{id}/cards", deckId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"BASIC\",\"front\":\"Front\",\"back\":\"Back\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String cardId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/review/answer")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\":\"" + cardId + "\",\"rating\":\"PERFECT\",\"msTaken\":1000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aiEndpointsReturn503WhenNotConfigured() throws Exception {
        String token = registerAndGetToken();

        mvc.perform(post("/api/v1/ai/generate")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"task\":\"FLASHCARDS\",\"sourceText\":\"Two plus two equals four.\",\"count\":4}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));

        mvc.perform(post("/api/v1/ai/tutor")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What is two plus two?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
    }

    private String createDeck(String token) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/decks")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Algebra warm-ups\",\"color\":\"#6366f1\",\"icon\":\"∑\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(id).isNotBlank();
        return id;
    }

    private String registerAndGetToken() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"student-%s@example.com\",\"password\":\"password123\",\"nickname\":\"Student\"}"
                        .formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
