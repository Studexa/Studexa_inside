package com.studexa.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        "app.jwt.secret=integration-test-secret-0123456789abcdef"
})
class SubjectIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void subjectsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/subjects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void createListUpdateArchiveAndDelete() throws Exception {
        String token = registerAndGetToken();

        String id = createSubject(token, "Mathematics", "#6366f1", "\uD83D\uDCD0");

        mvc.perform(get("/api/v1/subjects").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].name").value("Mathematics"))
                .andExpect(jsonPath("$[0].color").value("#6366f1"))
                .andExpect(jsonPath("$[0].icon").value("\uD83D\uDCD0"))
                .andExpect(jsonPath("$[0].archived").value(false));

        mvc.perform(put("/api/v1/subjects/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  Advanced Mathematics  \",\"color\":\"#0ea5e9\",\"archived\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Advanced Mathematics"))
                .andExpect(jsonPath("$.color").value("#0ea5e9"))
                .andExpect(jsonPath("$.archived").value(true));

        mvc.perform(get("/api/v1/subjects").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].archived").value(true));

        mvc.perform(get("/api/v1/subjects").param("archived", "false")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/v1/subjects").param("archived", "true")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(delete("/api/v1/subjects/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/subjects").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void blankNameIsRejected() throws Exception {
        String token = registerAndGetToken();

        mvc.perform(post("/api/v1/subjects")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String id = createSubject(token, "Physics", null, null);
        mvc.perform(put("/api/v1/subjects/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_NAME"));
    }

    @Test
    void anotherUserGetsNotFoundForSomeoneElsesSubject() throws Exception {
        String alice = registerAndGetToken();
        String bob = registerAndGetToken();
        String aliceSubjectId = createSubject(alice, "Biology", null, null);

        mvc.perform(put("/api/v1/subjects/{id}", aliceSubjectId)
                .header(HttpHeaders.AUTHORIZATION, bearer(bob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Stolen\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(delete("/api/v1/subjects/{id}", aliceSubjectId)
                .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/subjects").header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Biology"));
    }

    private String createSubject(String token, String name, String color, String icon) throws Exception {
        String body = color == null && icon == null
                ? "{\"name\":\"%s\"}".formatted(name)
                : "{\"name\":\"%s\",\"color\":\"%s\",\"icon\":\"%s\"}".formatted(name, color, icon);

        MvcResult result = mvc.perform(post("/api/v1/subjects")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
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
