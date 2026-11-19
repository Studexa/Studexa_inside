package com.studexa.quiz;

import java.util.List;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.common.ApiException;
import com.studexa.common.CurrentUser;
import com.studexa.quiz.dto.QuizDto;
import com.studexa.quiz.dto.QuizDto.QuestionRequest;
import com.studexa.quiz.dto.QuizDto.QuizRequest;

import jakarta.validation.Valid;

/**
 * Quiz management: create quizzes, list them, add questions.
 */
@RestController
@RequestMapping("/api/v1/quizzes")
public class QuizController {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    public QuizController(QuizRepository quizRepository, QuestionRepository questionRepository,
            ObjectMapper objectMapper) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDto.QuizResponse createQuiz(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody QuizRequest request) {
        UUID ownerId = CurrentUser.id(jwt);
        if (request.title() == null || request.title().isBlank()) {
            throw ApiException.badRequest("INVALID_TITLE", "Quiz title is required.");
        }
        Quiz quiz = new Quiz(
                ownerId,
                request.title().trim(),
                request.description(),
                request.subject(),
                visibility(request.visibility()));
        quiz = quizRepository.save(quiz);
        return toResponse(quiz);
    }

    @GetMapping
    public List<QuizDto.QuizResponse> listQuizzes(@AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = CurrentUser.id(jwt);
        return quizRepository.findByOwnerId(ownerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/public")
    public List<QuizDto.QuizResponse> listPublicQuizzes() {
        return quizRepository.findPublicQuizzes().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public QuizWithQuestions getQuiz(@PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = CurrentUser.id(jwt);
        Quiz quiz = quizRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("Quiz not found."));
        List<QuizDto.QuestionResponse> questions = questionRepository.findByQuizIdOrderByCreatedAtAscIdAsc(id).stream()
                .map(this::toResponse)
                .toList();
        return new QuizWithQuestions(toResponse(quiz), questions);
    }

    @PostMapping("/{id}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDto.QuestionResponse addQuestion(@PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody QuestionRequest request) {
        UUID ownerId = CurrentUser.id(jwt);
        Quiz quiz = quizRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("Quiz not found."));
        if (request.text() == null || request.text().isBlank()) {
            throw ApiException.badRequest("INVALID_QUESTION", "Question text is required.");
        }
        if (request.correctAnswer() == null || request.correctAnswer().isBlank()) {
            throw ApiException.badRequest("INVALID_QUESTION", "A correct answer is required.");
        }
        Question.Type questionType = type(request.type());
        int timeLimit = request.timeLimitSeconds() == null ? 30 : request.timeLimitSeconds();
        if (timeLimit < 5 || timeLimit > 600) {
            throw ApiException.badRequest("INVALID_TIME_LIMIT", "Question time must be between 5 and 600 seconds.");
        }
        if (questionType == Question.Type.MULTIPLE_CHOICE
                && !hasValidChoices(request.options(), request.correctAnswer())) {
            throw ApiException.badRequest("INVALID_OPTIONS",
                    "A multiple-choice question needs at least two choices, including the correct answer.");
        }
        Question question = new Question(
                quiz.getId(),
                request.text().trim(),
                questionType,
                request.options(),
                request.correctAnswer().trim(),
                request.explanation(),
                request.subject(),
                request.topic(),
                request.subtopic(),
                difficulty(request.difficulty()),
                timeLimit);
        question = questionRepository.save(question);
        return toResponse(question);
    }

    private QuizDto.QuizResponse toResponse(Quiz quiz) {
        return new QuizDto.QuizResponse(
                quiz.getId(), quiz.getOwnerId(), quiz.getTitle(), quiz.getDescription(),
                quiz.getSubject(), quiz.getVisibility().name(), quiz.getCreatedAt());
    }

    private QuizDto.QuestionResponse toResponse(Question q) {
        return new QuizDto.QuestionResponse(
                q.getId(), q.getQuizId(), q.getText(), q.getType().name(),
                q.getOptions(), q.getCorrectAnswer(), q.getExplanation(),
                q.getSubject(), q.getTopic(), q.getSubtopic(),
                q.getDifficulty().name(), q.getTimeLimitSeconds(), q.getCreatedAt());
    }

    private static Quiz.Visibility visibility(String value) {
        if (value == null || value.isBlank()) {
            return Quiz.Visibility.PRIVATE;
        }
        try {
            return Quiz.Visibility.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_VISIBILITY", "Visibility must be PRIVATE or PUBLIC.");
        }
    }

    private static Question.Type type(String value) {
        if (value == null || value.isBlank()) {
            return Question.Type.MULTIPLE_CHOICE;
        }
        try {
            return Question.Type.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_QUESTION_TYPE", "Unknown question type.");
        }
    }

    private static Question.Difficulty difficulty(String value) {
        if (value == null || value.isBlank()) {
            return Question.Difficulty.MEDIUM;
        }
        try {
            return Question.Difficulty.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_DIFFICULTY", "Unknown difficulty.");
        }
    }

    private boolean hasValidChoices(String options, String correctAnswer) {
        if (options == null || options.isBlank()) return false;
        try {
            JsonNode node = objectMapper.readTree(options);
            if (!node.isArray() || node.size() < 2) return false;
            boolean correctFound = false;
            java.util.Set<String> choices = new java.util.HashSet<>();
            for (JsonNode option : node) {
                String choice = option.isTextual() ? option.asText()
                        : option.hasNonNull("id") ? option.get("id").asText()
                        : option.hasNonNull("text") ? option.get("text").asText() : null;
                if (choice == null || choice.isBlank()) return false;
                String normalized = choice.trim().toLowerCase(java.util.Locale.ROOT);
                if (!choices.add(normalized)) return false;
                if (normalized.equals(correctAnswer.trim().toLowerCase(java.util.Locale.ROOT))) {
                    correctFound = true;
                }
                if (!option.isTextual() && option.hasNonNull("text")
                        && option.get("text").asText().trim().equalsIgnoreCase(correctAnswer.trim())) {
                    correctFound = true;
                }
            }
            return correctFound;
        } catch (JacksonException ex) {
            return false;
        }
    }

    public record QuizWithQuestions(QuizDto.QuizResponse quiz, List<QuizDto.QuestionResponse> questions) {}
}
