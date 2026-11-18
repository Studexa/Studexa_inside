package com.studexa.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.studexa.ai.dto.AiGenerateRequest;
import com.studexa.ai.dto.AiGenerateResponse;
import com.studexa.ai.dto.AiTask;
import com.studexa.ai.dto.AiTutorRequest;
import com.studexa.ai.dto.AiTutorResponse;
import com.studexa.deck.CardRepository;
import com.studexa.deck.DeckRepository;
import com.studexa.deck.CardType;
import com.studexa.subject.SubjectRepository;
import com.studexa.common.ApiException;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private AnthropicClient anthropicClient;
    @Mock
    private AiUsageRepository usageRepository;
    @Mock
    private DeckRepository deckRepository;
    @Mock
    private CardRepository cardRepository;
    @Mock
    private SubjectRepository subjectRepository;

    private AiService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties(
                "test-key", "claude-test", 10, 12000, Duration.ofSeconds(5),
                null, null, false);
        // AiService.client() picks the AnthropicClient from a real list; a
        // mocked List cannot have its stream() default method stubbed.
        service = new AiService(List.of(anthropicClient), properties, usageRepository,
                deckRepository, cardRepository, subjectRepository,
                JsonMapper.builder().build(), Clock.fixed(Instant.parse("2026-11-17T23:59:00Z"), ZoneOffset.UTC));
        // The service claims a daily quota slot via tryIncrementUsage; a
        // return of 1 means the slot was claimed successfully.
        when(usageRepository.tryIncrementUsage(any(), any(), anyInt())).thenReturn(1);
    }

    @Test
    void generateKeepsOnlyExactSourceExcerpts() {
        when(anthropicClient.complete(anyString(), anyString())).thenReturn("""
                {"items":[
                  {"type":"BASIC","front":"What do plants use?","back":"Sunlight",
                   "choices":[],"explanation":"Plants need light.","sourceTitle":"Notes",
                   "sourceExcerpt":"Plants use sunlight to make food."},
                  {"type":"BASIC","front":"What is chlorophyll?","back":"A pigment",
                   "choices":[],"explanation":"Made up.","sourceTitle":"Notes",
                   "sourceExcerpt":"Chlorophyll absorbs moonlight."}
                ]}
                """);

        AiGenerateResponse response = service.generate(UUID.randomUUID(),
                new AiGenerateRequest(AiTask.FLASHCARDS, null,
                        "Plants use sunlight to make food. Chlorophyll is green.",
                        "Notes", 4, "en"));

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getFirst().sourceExcerpt())
                .isEqualTo("Plants use sunlight to make food.");
        assertThat(response.items().get(1).sourceExcerpt()).isNull();
    }

    @Test
    void quizDropsQuestionsWhoseAnswerIsNotAChoice() {
        when(anthropicClient.complete(anyString(), anyString())).thenReturn("""
                {"items":[
                  {"type":"MULTIPLE_CHOICE","front":"What is 2 + 2?","back":"4",
                   "choices":["3","4","5"],"explanation":"Two plus two is four.",
                   "sourceTitle":"Math","sourceExcerpt":"Two plus two equals four."},
                  {"type":"MULTIPLE_CHOICE","front":"What is 3 + 3?","back":"6",
                   "choices":["2","4","5"],"explanation":"Broken choices.",
                   "sourceTitle":"Math","sourceExcerpt":""}
                ]}
                """);

        AiGenerateResponse response = service.generate(UUID.randomUUID(),
                new AiGenerateRequest(AiTask.QUIZ, null,
                        "Two plus two equals four. Three plus three equals six.",
                        "Math", 2, "en"));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().type()).isEqualTo(CardType.MULTIPLE_CHOICE);
        assertThat(response.items().getFirst().front()).isEqualTo("What is 2 + 2?");
    }

    @Test
    void tutorDropsReferencesThatAreNotInTheSource() {
        when(anthropicClient.complete(anyString(), anyString())).thenReturn("""
                {"answer":"Plants use sunlight to make food.",
                 "references":[
                   {"title":"Notes","sourceExcerpt":"Plants use sunlight to make food."},
                   {"title":"Notes","sourceExcerpt":"Plants eat soil for energy."}
                 ]}
                """);

        AiTutorResponse response = service.tutor(UUID.randomUUID(),
                new AiTutorRequest("How do plants make food?",
                        "Plants use sunlight to make food. Chlorophyll is green.", "Notes"));

        assertThat(response.answer()).isEqualTo("Plants use sunlight to make food.");
        assertThat(response.references()).hasSize(1);
        assertThat(response.references().getFirst().excerpt())
                .isEqualTo("Plants use sunlight to make food.");
    }

    @Test
    void rejectsEmptyGeneratedCardsAndRefundsQuota() {
        when(anthropicClient.complete(anyString(), anyString())).thenReturn(
                "{\"items\":[{\"type\":\"BASIC\",\"front\":\"   \",\"back\":\"\"}]}");
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> service.generate(userId,
                new AiGenerateRequest(AiTask.FLASHCARDS, null, "Study notes", "Notes", 1, "en")))
                .isInstanceOf(ApiException.class);
        verify(usageRepository).decrementUsage(userId, LocalDate.of(2026, 11, 17));
    }

    @Test
    void rejectsOversizedTutorAnswerAndRefundsQuota() {
        when(anthropicClient.complete(anyString(), anyString())).thenReturn(
                "{\"answer\":\"" + "a".repeat(6001) + "\",\"references\":[]}");
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> service.tutor(userId, new AiTutorRequest("Explain", null, null)))
                .isInstanceOf(ApiException.class);
        verify(usageRepository).decrementUsage(userId, LocalDate.of(2026, 11, 17));
    }

    @Test
    void citationsMustMatchExactlyAndUseAttachedTitle() {
        when(anthropicClient.complete(anyString(), anyString())).thenReturn("""
                {"answer":"Light helps plants.","references":[
                  {"title":"Invented book","sourceExcerpt":"Plants use sunlight."},
                  {"title":"Invented book","sourceExcerpt":"plants use sunlight."}]}
                """);
        var response = service.tutor(UUID.randomUUID(),
                new AiTutorRequest("Explain", "Plants use sunlight.", "My biology notes"));
        assertThat(response.references()).hasSize(1);
        assertThat(response.references().getFirst().title()).isEqualTo("My biology notes");
    }

    @Test
    void rejectsQuizWithOnlyTwoChoices() {
        when(anthropicClient.complete(anyString(), anyString())).thenReturn("""
                {"items":[{"type":"MULTIPLE_CHOICE","front":"Choose","back":"a","choices":["a","b"]}]}
                """);
        assertThatThrownBy(() -> service.generate(UUID.randomUUID(),
                new AiGenerateRequest(AiTask.QUIZ, null, "Study notes", "Notes", 1, "en")))
                .isInstanceOf(ApiException.class);
    }
}
