package com.studexa.quiz.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data transfer objects for the quiz module. All records are nested inside
 * QuizDto so they can be referenced as QuizDto.Xxx from controllers.
 */
public class QuizDto {

    public record QuizRequest(
            String title,
            String description,
            String subject,
            String visibility
    ) {}

    public record QuizResponse(
            UUID id,
            UUID ownerId,
            String title,
            String description,
            String subject,
            String visibility,
            Instant createdAt
    ) {}

    public record QuestionRequest(
            String text,
            String type,
            String options,
            String correctAnswer,
            String explanation,
            String subject,
            String topic,
            String subtopic,
            String difficulty,
            Integer timeLimitSeconds
    ) {}

    public record QuestionResponse(
            UUID id,
            UUID quizId,
            String text,
            String type,
            String options,
            String correctAnswer,
            String explanation,
            String subject,
            String topic,
            String subtopic,
            String difficulty,
            Integer timeLimitSeconds,
            Instant createdAt
    ) {}

    public record CreateSessionRequest(
            UUID quizId,
            String mode,
            Integer numQuestions,
            Integer timePerQuestion,
            String difficulty
    ) {}

    public record SessionResponse(
            UUID id,
            UUID quizId,
            UUID hostId,
            String roomCode,
            String mode,
            String status,
            String settings,
            Instant startedAt,
            Instant endedAt,
            Instant createdAt
    ) {}

    public record SessionPlayerResponse(
            UUID id,
            UUID sessionId,
            UUID userId,
            String nickname,
            String avatarUrl,
            String teamId,
            Integer totalScore,
            Integer finalRank,
            Boolean isReady,
            Boolean isHost
    ) {}

    public record JoinRoomRequest(
            String roomCode,
            String nickname
    ) {}

    public record RoomInfoResponse(
            SessionResponse session,
            List<SessionPlayerResponse> players
    ) {}

    public record AnswerRequest(
            UUID sessionPlayerId,
            UUID questionId,
            String selectedAnswer,
            Integer responseTimeMs
    ) {}

    public record AnswerResponse(
            UUID id,
            Boolean isCorrect,
            Integer scoreAwarded,
            String correctAnswer,
            String explanation
    ) {}

    public record LeaderboardEntry(
            String nickname,
            Integer totalScore,
            Integer finalRank,
            Boolean isHost
    ) {}
}
