package com.studexa.quiz.websocket;

import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.studexa.quiz.dto.QuizDto.SessionPlayerResponse;

/**
 * STOMP message handler for real-time quiz events.
 * Clients subscribe to /topic/room/{roomCode} for room broadcasts.
 */
@Controller
public class QuizStompController {

    private final SimpMessagingTemplate messagingTemplate;

    public QuizStompController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Client signals they are ready in the lobby.
     * Payload: { roomCode, playerId, isReady }
     */
    @MessageMapping("/quiz.ready")
    public void onReady(@Payload ReadyPayload payload) {
        messagingTemplate.convertAndSend("/topic/room/" + payload.roomCode(), payload);
    }

    /**
     * Host starts the quiz.
     * Payload: { roomCode, sessionId }
     */
    @MessageMapping("/quiz.start")
    public void onStart(@Payload StartPayload payload) {
        messagingTemplate.convertAndSend("/topic/room/" + payload.roomCode(), payload);
    }

    /**
     * Host moves to next question.
     * Payload: { roomCode, questionIndex, question, endsAt }
     */
    @MessageMapping("/quiz.next")
    public void onNextQuestion(@Payload NextQuestionPayload payload) {
        messagingTemplate.convertAndSend("/topic/room/" + payload.roomCode(), payload);
    }

    /**
     * Host ends the game.
     * Payload: { roomCode, leaderboard }
     */
    @MessageMapping("/quiz.end")
    public void onEnd(@Payload EndPayload payload) {
        messagingTemplate.convertAndSend("/topic/room/" + payload.roomCode(), payload);
    }

    /**
     * Player submits an answer (optimistic, server also persists via REST).
     * Payload: { roomCode, playerId, questionId, isCorrect, score }
     */
    @MessageMapping("/quiz.answer")
    public void onAnswer(@Payload AnswerPayload payload) {
        messagingTemplate.convertAndSend("/topic/room/" + payload.roomCode(), payload);
    }

    public record ReadyPayload(String roomCode, UUID playerId, Boolean isReady) {}
    public record StartPayload(String roomCode, UUID sessionId) {}
    public record NextQuestionPayload(String roomCode, Integer questionIndex, Object question, Long endsAt) {}
    public record EndPayload(String roomCode, Object leaderboard) {}
    public record AnswerPayload(String roomCode, UUID playerId, UUID questionId, Boolean isCorrect, Integer score) {}
}
