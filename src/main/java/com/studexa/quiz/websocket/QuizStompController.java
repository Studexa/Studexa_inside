package com.studexa.quiz.websocket;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import com.studexa.quiz.AnswerRepository;
import com.studexa.quiz.LiveQuizService;
import com.studexa.quiz.Session;
import com.studexa.quiz.SessionPlayer;
import com.studexa.quiz.SessionPlayerRepository;
import com.studexa.quiz.SessionRepository;

/**
 * STOMP message handler for real-time quiz events.
 *
 * <p>Room updates only notify players to reload their own server state. Host
 * actions use the same rules as the REST API and scores are calculated by the server.
 *
 * <p>Clients subscribe to {@code /topic/room/{roomCode}}.
 */
@Controller
public class QuizStompController {

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionRepository sessionRepository;
    private final SessionPlayerRepository playerRepository;
    private final LiveQuizService liveQuiz;
    private final AnswerRepository answerRepository;

    public QuizStompController(SimpMessagingTemplate messagingTemplate,
            SessionRepository sessionRepository,
            SessionPlayerRepository playerRepository,
            LiveQuizService liveQuiz,
            AnswerRepository answerRepository) {
        this.messagingTemplate = messagingTemplate;
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.liveQuiz = liveQuiz;
        this.answerRepository = answerRepository;
    }

    /** Player toggles readiness in the lobby. Payload: { roomCode, isReady }. */
    @MessageMapping("/quiz.ready")
    @Transactional
    public void onReady(@Payload ReadyPayload payload, Principal principal) {
        Session session = requireRoom(payload.roomCode());
        SessionPlayer player = requirePlayer(session, principal);

        player.setIsReady(Boolean.TRUE.equals(payload.isReady()));
        playerRepository.save(player);

        List<SessionPlayer> players = playerRepository.findBySessionId(session.getId());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "PLAYER_READY");
        event.put("playerId", player.getId().toString());
        event.put("nickname", player.getNickname());
        event.put("isReady", player.getIsReady());
        event.put("playerCount", players.size());
        broadcast(session, event);
    }

    @MessageMapping("/quiz.start")
    public void onStart(@Payload StartPayload payload, Principal principal) {
        liveQuiz.start(requireRoom(payload.roomCode()).getId(), userId(principal));
    }

    @MessageMapping("/quiz.next")
    public void onNextQuestion(@Payload NextQuestionPayload payload, Principal principal) {
        if (payload.questionId() == null) throw new MessagingException("questionId is required.");
        liveQuiz.advanceTo(requireRoom(payload.roomCode()).getId(), userId(principal), payload.questionId());
    }

    /**
     * Player signals that an answer was submitted (the answer itself is stored by
     * {@code POST /api/v1/answers}). Payload: { roomCode, questionId }.
     */
    @MessageMapping("/quiz.answer")
    public void onAnswer(@Payload AnswerPayload payload, Principal principal) {
        Session session = requireRoom(payload.roomCode());
        SessionPlayer player = requirePlayer(session, principal);
        if (payload.questionId() == null) {
            return;
        }

        List<UUID> playerIds = playerRepository.findBySessionId(session.getId()).stream()
                .map(SessionPlayer::getId)
                .toList();
        long answered = answerRepository.countForQuestion(payload.questionId(), playerIds);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "ANSWER_RECEIVED");
        event.put("playerId", player.getId().toString());
        event.put("questionId", payload.questionId().toString());
        event.put("answeredCount", answered);
        event.put("playerCount", playerIds.size());
        broadcast(session, event);
    }

    @MessageMapping("/quiz.end")
    public void onEnd(@Payload EndPayload payload, Principal principal) {
        liveQuiz.finish(requireRoom(payload.roomCode()).getId(), userId(principal));
    }

    @TransactionalEventListener
    public void onRoomChanged(LiveQuizService.RoomChanged event) {
        messagingTemplate.convertAndSend("/topic/room/" + event.roomCode(), (Object) Map.of("type", "ROOM_UPDATED"));
    }

    private Session requireRoom(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            throw new MessagingException("roomCode is required.");
        }
        return sessionRepository.findByRoomCode(roomCode.trim().toUpperCase())
                .orElseThrow(() -> new MessagingException("Room not found."));
    }

    private static UUID userId(Principal principal) {
        if (principal == null) throw new MessagingException("Authentication required.");
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException ex) {
            throw new MessagingException("Authentication required.", ex);
        }
    }

    private SessionPlayer requirePlayer(Session session, Principal principal) {
        return playerRepository.findBySessionIdAndUserId(session.getId(), userId(principal))
                .orElseThrow(() -> new MessagingException("You are not in this room."));
    }
    private void broadcast(Session session, Map<String, Object> event) {
        messagingTemplate.convertAndSend("/topic/room/" + session.getRoomCode(), (Object) event);
    }

    public record ReadyPayload(String roomCode, Boolean isReady) {}

    public record StartPayload(String roomCode) {}

    public record NextQuestionPayload(String roomCode, Integer questionIndex, UUID questionId, Long endsAt) {}

    public record AnswerPayload(String roomCode, UUID questionId) {}

    public record EndPayload(String roomCode) {}
}
