package com.studexa.quiz;

import java.util.List;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.common.CurrentUser;
import com.studexa.quiz.dto.QuizDto;
import com.studexa.quiz.dto.QuizDto.AnswerRequest;

import jakarta.validation.Valid;

/**
 * Answer submission during a live quiz session.
 *
 * <p>Security rules (doc §9.3): the caller can only answer as their own player,
 * the question must belong to the session's quiz, one answer per question is
 * accepted, and correctness plus score are computed here — never taken from the
 * client. The client only supplies which option was picked; response time is
 * measured from the server-side timestamp for the active question.
 */
@RestController
@RequestMapping("/api/v1/answers")
public class AnswerController {

    private static final int BASE_SCORE = 1000;
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final AnswerRepository answerRepository;
    private final SessionPlayerRepository playerRepository;
    private final QuestionRepository questionRepository;
    private final SessionRepository sessionRepository;
    private final ApplicationEventPublisher events;

    public AnswerController(AnswerRepository answerRepository,
            SessionPlayerRepository playerRepository,
            QuestionRepository questionRepository,
            SessionRepository sessionRepository, ApplicationEventPublisher events) {
        this.answerRepository = answerRepository;
        this.playerRepository = playerRepository;
        this.questionRepository = questionRepository;
        this.sessionRepository = sessionRepository;
        this.events = events;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public QuizDto.AnswerResponse submitAnswer(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AnswerRequest request) {
        UUID userId = CurrentUser.id(jwt);

        if (request.sessionPlayerId() == null || request.questionId() == null) {
            throw ApiException.badRequest("INVALID_ANSWER", "Player and question are required.");
        }
        if (request.selectedAnswer() == null || request.selectedAnswer().isBlank()) {
            throw ApiException.badRequest("INVALID_ANSWER", "Select an answer first.");
        }

        UUID sessionId = playerRepository.findOwnedSessionId(request.sessionPlayerId(), userId)
                .orElseThrow(() -> ApiException.notFound("Player not found."));
        Session session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> ApiException.notFound("Session not found."));
        SessionPlayer player = playerRepository.findByIdForUpdate(request.sessionPlayerId())
                .orElseThrow(() -> ApiException.notFound("Player not found."));
        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> ApiException.notFound("Question not found."));

        if (!question.getQuizId().equals(session.getQuizId())) {
            throw ApiException.notFound("Question not found.");
        }
        if (session.getStatus() != Session.Status.ACTIVE) {
            throw ApiException.conflict("SESSION_NOT_ACTIVE", "This quiz is not running.");
        }
        if (!question.getId().equals(session.getCurrentQuestionId())
                || session.getCurrentQuestionStartedAt() == null) {
            throw ApiException.conflict("QUESTION_NOT_ACTIVE", "This question is not currently active.");
        }

        // One answer per player per question (retries are not re-scored).
        if (!answerRepository.findBySessionPlayerIdAndQuestionId(player.getId(), question.getId()).isEmpty()) {
            throw ApiException.conflict("ALREADY_ANSWERED", "You already answered this question.");
        }

        boolean isCorrect = isCorrectAnswer(question, request.selectedAnswer().trim());

        if (question.getType() == Question.Type.MULTIPLE_CHOICE
                && !isValidOption(question.getOptions(), request.selectedAnswer().trim())) {
            throw ApiException.badRequest("INVALID_ANSWER", "Choose one of the available options.");
        }

        int timeLimitMs = LiveQuizService.timeLimit(question) * 1000;
        long elapsedMs = Math.max(0, Duration.between(session.getCurrentQuestionStartedAt(), Instant.now()).toMillis());
        if (elapsedMs > timeLimitMs) {
            throw ApiException.conflict("ANSWER_TIME_EXPIRED", "The time for this question has expired.");
        }
        int responseTimeMs = (int) elapsedMs;
        int score = calculateScore(isCorrect, responseTimeMs, timeLimitMs);

        Answer answer = new Answer(
                player.getId(), question.getId(), request.selectedAnswer().trim(),
                isCorrect, responseTimeMs, score);
        answer = answerRepository.save(answer);

        player.setTotalScore(player.getTotalScore() + score);
        playerRepository.save(player);
        events.publishEvent(new LiveQuizService.RoomChanged(session.getRoomCode()));

        return new QuizDto.AnswerResponse(
                answer.getId(), isCorrect, score,
                question.getCorrectAnswer(), question.getExplanation());
    }

    @GetMapping("/session/{sessionId}/player/{playerId}")
    public List<QuizDto.AnswerResponse> getPlayerAnswers(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @PathVariable UUID playerId) {
        SessionPlayer player = requireOwnedPlayer(CurrentUser.id(jwt), playerId);
        if (!player.getSessionId().equals(sessionId)) {
            throw ApiException.notFound("Player not found.");
        }
        return answerRepository.findBySessionPlayerId(playerId).stream()
                .map(a -> new QuizDto.AnswerResponse(
                        a.getId(), a.getIsCorrect(), a.getScoreAwarded(),
                        null, null))
                .toList();
    }

    private SessionPlayer requireOwnedPlayer(UUID userId, UUID playerId) {
        SessionPlayer player = playerRepository.findById(playerId)
                .orElseThrow(() -> ApiException.notFound("Player not found."));
        if (player.getUserId() == null || !player.getUserId().equals(userId)) {
            // 404 rather than 403 so player ids cannot be probed.
            throw ApiException.notFound("Player not found.");
        }
        return player;
    }

    private static boolean isValidOption(String options, String selected) {
        if (options == null || options.isBlank()) return false;
        try {
            com.fasterxml.jackson.databind.JsonNode node = JSON.readTree(options);
            if (!node.isArray()) return false;
            for (com.fasterxml.jackson.databind.JsonNode option : node) {
                String value = option.isTextual() ? option.asText()
                        : option.hasNonNull("text") ? option.get("text").asText() : null;
                String id = option.hasNonNull("id") ? option.get("id").asText() : null;
                if ((value != null && value.trim().equalsIgnoreCase(selected))
                        || (id != null && id.trim().equalsIgnoreCase(selected))) return true;
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return false;
        }
        return false;
    }

    private static boolean isCorrectAnswer(Question question, String selected) {
        String correct = question.getCorrectAnswer();
        if (correct == null) return false;
        if (correct.equalsIgnoreCase(selected)) return true;
        if (question.getOptions() == null) return false;
        try {
            com.fasterxml.jackson.databind.JsonNode options = JSON.readTree(question.getOptions());
            if (!options.isArray()) return false;
            for (com.fasterxml.jackson.databind.JsonNode option : options) {
                if (option.isTextual()) continue;
                String id = option.hasNonNull("id") ? option.get("id").asText() : null;
                String text = option.hasNonNull("text") ? option.get("text").asText() : null;
                boolean selectedThisOption = (id != null && id.equalsIgnoreCase(selected))
                        || (text != null && text.equalsIgnoreCase(selected));
                boolean optionIsCorrect = (id != null && id.equalsIgnoreCase(correct))
                        || (text != null && text.equalsIgnoreCase(correct));
                if (selectedThisOption && optionIsCorrect) return true;
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return false;
        }
        return false;
    }

    private int calculateScore(boolean isCorrect, int responseTimeMs, int timeLimitMs) {
        if (!isCorrect) {
            return 0;
        }
        double timeRatio = Math.max(0, Math.min(1, (double) (timeLimitMs - responseTimeMs) / timeLimitMs));
        return (int) (BASE_SCORE * (0.5 + 0.5 * timeRatio));
    }
}
