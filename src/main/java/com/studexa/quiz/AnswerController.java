package com.studexa.quiz;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.quiz.dto.QuizDto;
import com.studexa.quiz.dto.QuizDto.AnswerRequest;

import jakarta.validation.Valid;

/**
 * Answer submission during a live quiz session.
 */
@RestController
@RequestMapping("/api/v1/answers")
public class AnswerController {

    private final AnswerRepository answerRepository;
    private final SessionPlayerRepository playerRepository;
    private final QuestionRepository questionRepository;

    public AnswerController(AnswerRepository answerRepository,
            SessionPlayerRepository playerRepository, QuestionRepository questionRepository) {
        this.answerRepository = answerRepository;
        this.playerRepository = playerRepository;
        this.questionRepository = questionRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDto.AnswerResponse submitAnswer(@Valid @RequestBody AnswerRequest request) {
        SessionPlayer player = playerRepository.findById(request.sessionPlayerId())
                .orElseThrow(() -> new RuntimeException("Player not found"));
        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new RuntimeException("Question not found"));

        boolean isCorrect = question.getCorrectAnswer().equalsIgnoreCase(request.selectedAnswer());
        int score = calculateScore(isCorrect, request.responseTimeMs());

        Answer answer = new Answer(
                player.getId(), question.getId(), request.selectedAnswer(),
                isCorrect, request.responseTimeMs(), score);
        answer = answerRepository.save(answer);

        // Update player's total score
        player.setTotalScore(player.getTotalScore() + score);
        playerRepository.save(player);

        return new QuizDto.AnswerResponse(
                answer.getId(), isCorrect, score,
                question.getCorrectAnswer(), question.getExplanation());
    }

    @GetMapping("/session/{sessionId}/player/{playerId}")
    public List<QuizDto.AnswerResponse> getPlayerAnswers(@PathVariable UUID sessionId,
            @PathVariable UUID playerId) {
        return answerRepository.findBySessionPlayerId(playerId).stream()
                .map(a -> new QuizDto.AnswerResponse(
                        a.getId(), a.getIsCorrect(), a.getScoreAwarded(),
                        null, null))
                .toList();
    }

    private int calculateScore(boolean isCorrect, Integer responseTimeMs) {
        if (!isCorrect) return 0;
        int base = 1000;
        int timeLimit = 30000;
        int timeTaken = responseTimeMs != null ? responseTimeMs : timeLimit;
        double timeRatio = Math.max(0, Math.min(1, (double) (timeLimit - timeTaken) / timeLimit));
        return (int) (base * (0.5 + 0.5 * timeRatio));
    }
}
