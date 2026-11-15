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

    public QuizController(QuizRepository quizRepository, QuestionRepository questionRepository) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDto.QuizResponse createQuiz(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody QuizRequest request) {
        UUID ownerId = CurrentUser.id(jwt);
        Quiz quiz = new Quiz(
                ownerId,
                request.title(),
                request.description(),
                request.subject(),
                Quiz.Visibility.valueOf(request.visibility()));
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
                .orElseThrow(() -> new RuntimeException("Quiz not found"));
        List<QuizDto.QuestionResponse> questions = questionRepository.findByQuizIdOrderByCreatedAtAsc(id).stream()
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
                .orElseThrow(() -> new RuntimeException("Quiz not found"));
        Question question = new Question(
                quiz.getId(),
                request.text(),
                Question.Type.valueOf(request.type()),
                request.options(),
                request.correctAnswer(),
                request.explanation(),
                request.subject(),
                request.topic(),
                request.subtopic(),
                Question.Difficulty.valueOf(request.difficulty()),
                request.timeLimitSeconds());
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

    public record QuizWithQuestions(QuizDto.QuizResponse quiz, List<QuizDto.QuestionResponse> questions) {}
}
