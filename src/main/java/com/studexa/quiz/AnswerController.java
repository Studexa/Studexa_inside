package com.studexa.quiz;

import java.util.List;
import java.util.UUID;

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
import com.studexa.quiz.dto.QuizDto.AnswerRequest;

import jakarta.validation.Valid;

/**
 * Answer submission during a live quiz session.
 *
 * <p>Security rules (doc §9.3): the caller can only answer as their own player,
 * the question must belong to the session's quiz, one answer per question is
 * accepted, and correctness plus score are computed here — never taken from the
 * client. The client only supplies which option was picked and how long it took,
 * and the reported time is clamped to the question's limit.
 */
@RestController
@RequestMapping("/api/v1/answers")
public class AnswerController {

    private static final int BASE_SCORE = 1000;
    private static final int DEFAULT_TIME_LIMIT_SECONDS = 30;

    private final AnswerRepository answerRepository;
    private final SessionPlayerRepository playerRepository;
    private final QuestionRepository questionRepository;
    private final SessionRepository sessionRepository;

    public AnswerController(AnswerRepository answerRepository,
            SessionPlayerRepository playerRepository,
            QuestionRepository questionRepository,
            SessionRepository sessionRepository) {
        this.answerRepository = answerRepository;
        this.playerRepository = playerRepository;
        this.questionRepository = questionRepository;
        this.sessionRepository = sessionRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDto.AnswerResponse submitAnswer(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AnswerRequest request) {
        UUID userId = CurrentUser.id(jwt);

        if (request.sessionPlayerId() == null || request.questionId() == null) {
            throw ApiException.badRequest("INVALID_ANSWER", "Player and question are required.");
        }
        if (request.selectedAnswer() == null || request.selectedAnswer().isBlank()) {
            throw ApiException.badRequest("INVALID_ANSWER", "Select an answer first.");
        }

        SessionPlayer player = requireOwnedPlayer(userId, request.sessionPlayerId());
        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> ApiException.notFound("Question not found."));

        Session session = sessionRepository.findById(player.getSessionId())
                .orElseThrow(() -> ApiException.notFound("Session not found."));
        if (!question.getQuizId().equals(session.getQuizId())) {
            throw ApiException.notFound("Question not found.");
        }
        if (session.getStatus() != Session.Status.ACTIVE) {
            throw ApiException.conflict("SESSION_NOT_ACTIVE", "This quiz is not running.");
        }

        // One answer per player per question (retries are not re-scored).
        if (!answerRepository.findBySessionPlayerIdAndQuestionId(player.getId(), question.getId()).isEmpty()) {
            throw ApiException.conflict("ALREADY_ANSWERED", "You already answered this question.");
        }

        boolean isCorrect = question.getCorrectAnswer() != null
                && question.getCorrectAnswer().equalsIgnoreCase(request.selectedAnswer().trim());

        int timeLimitMs = timeLimitMs(question);
        int responseTimeMs = request.responseTimeMs() == null
                ? timeLimitMs
                : Math.max(0, Math.min(timeLimitMs, request.responseTimeMs()));
        int score = calculateScore(isCorrect, responseTimeMs, timeLimitMs);

        Answer answer = new Answer(
                player.getId(), question.getId(), request.selectedAnswer().trim(),
                isCorrect, responseTimeMs, score);
        answer = answerRepository.save(answer);

        player.setTotalScore(player.getTotalScore() + score);
        playerRepository.save(player);

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

    private static int timeLimitMs(Question question) {
        Integer seconds = question.getTimeLimitSeconds();
        int safe = seconds == null || seconds <= 0 ? DEFAULT_TIME_LIMIT_SECONDS : seconds;
        return safe * 1000;
    }

    private int calculateScore(boolean isCorrect, int responseTimeMs, int timeLimitMs) {
        if (!isCorrect) {
            return 0;
        }
        double timeRatio = Math.max(0, Math.min(1, (double) (timeLimitMs - responseTimeMs) / timeLimitMs));
        return (int) (BASE_SCORE * (0.5 + 0.5 * timeRatio));
    }
}
