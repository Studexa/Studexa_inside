package com.example.studyapp.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.studyapp.ai.dto.AiGenerateRequest;
import com.example.studyapp.ai.dto.AiGenerateResponse;
import com.example.studyapp.ai.dto.AiTask;
import com.example.studyapp.ai.dto.AiTutorRequest;
import com.example.studyapp.ai.dto.AiTutorResponse;
import com.example.studyapp.deck.CardRepository;
import com.example.studyapp.deck.DeckRepository;
import com.example.studyapp.deck.CardType;
import com.example.studyapp.subject.SubjectRepository;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private AiClient aiClient;
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
                "test-key", "claude-test", 10, 12000, Duration.ofSeconds(5));
        service = new AiService(aiClient, properties, usageRepository, deckRepository,
                cardRepository, subjectRepository, JsonMapper.builder().build());
        when(usageRepository.findById(any())).thenReturn(Optional.empty());
        when(usageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void generateKeepsOnlyExactSourceExcerpts() {
        when(aiClient.complete(anyString(), anyString())).thenReturn("""
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
        verify(usageRepository).save(any());
    }

    @Test
    void quizDropsQuestionsWhoseAnswerIsNotAChoice() {
        when(aiClient.complete(anyString(), anyString())).thenReturn("""
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
        when(aiClient.complete(anyString(), anyString())).thenReturn("""
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
}
