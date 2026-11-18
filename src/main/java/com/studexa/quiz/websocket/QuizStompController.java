package com.studexa.quiz.websocket;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
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

import com.studexa.quiz.AnswerRepository;
import com.studexa.quiz.Question;
import com.studexa.quiz.QuestionRepository;
import com.studexa.quiz.Session;
import com.studexa.quiz.SessionPlayer;
import com.studexa.quiz.SessionPlayerRepository;
import com.studexa.quiz.SessionRepository;

/**
 * STOMP message handler for real-time quiz events.
 *
 * <p>The server is authoritative (doc §9.3): it never forwards a client-supplied
 * "isCorrect" or "score", it loads questions from the database and strips the
 * correct answer before broadcasting, and host-only actions are checked against
 * the authenticated principal rather than trusted from the payload.
 *
 * <p>Clients subscribe to {@code /topic/room/{roomCode}}.
 */
@Controller
public class QuizStompController {

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionRepository sessionRepository;
    private final SessionPlayerRepository playerRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    public QuizStompController(SimpMessagingTemplate messagingTemplate,
            SessionRepository sessionRepository,
            SessionPlayerRepository playerRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository) {
        this.messagingTemplate = messagingTemplate;
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.questionRepository = questionRepository;
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

    /** Host starts the quiz. Payload: { roomCode }. */
    @MessageMapping("/quiz.start")
    @Transactional
    public void onStart(@Payload StartPayload payload, Principal principal) {
        Session session = requireHost(payload.roomCode(), principal);

        if (session.getStatus() == Session.Status.ENDED) {
            throw new MessagingException("This room has already finished.");
        }
        session.setStatus(Session.Status.ACTIVE);
        if (session.getStartedAt() == null) {
            session.setStartedAt(Instant.now());
        }
        sessionRepository.save(session);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "ROOM_STARTED");
        event.put("sessionId", session.getId().toString());
        event.put("startsAt", session.getStartedAt().toString());
        broadcast(session, event);
    }

    /**
     * Host advances to a question. Payload: { roomCode, questionIndex, questionId }.
     * The question is loaded server-side so the correct answer is never broadcast.
     */
    @MessageMapping("/quiz.next")
    @Transactional
    public void onNextQuestion(@Payload NextQuestionPayload payload, Principal principal) {
        Session session = requireHost(payload.roomCode(), principal);
        session = sessionRepository.findByIdForUpdate(session.getId())
                .orElseThrow(() -> new MessagingException("Room not found."));
        if (session.getStatus() != Session.Status.ACTIVE) {
            throw new MessagingException("This room is not active.");
        }
        if (payload.questionId() == null) {
            throw new MessagingException("questionId is required.");
        }

        Question question = questionRepository.findById(payload.questionId())
                .orElseThrow(() -> new MessagingException("Question not found."));
        if (!question.getQuizId().equals(session.getQuizId())) {
            throw new MessagingException("Question does not belong to this room's quiz.");
        }

        Instant startedAt = Instant.now();
        session.setCurrentQuestionId(question.getId());
        session.setCurrentQuestionStartedAt(startedAt);
        sessionRepository.save(session);
        int limitSeconds = question.getTimeLimitSeconds() == null
                ? 30 : Math.max(1, Math.min(600, question.getTimeLimitSeconds()));
        long endsAt = startedAt.plusSeconds(limitSeconds).toEpochMilli();
        broadcastQuestion(session, question, payload.questionIndex(), endsAt);
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

    /** Host ends the quiz. Payload: { roomCode }. Leaderboard is built server-side. */
    @MessageMapping("/quiz.end")
    @Transactional
    public void onEnd(@Payload EndPayload payload, Principal principal) {
        Session session = requireHost(payload.roomCode(), principal);
        session = sessionRepository.findByIdForUpdate(session.getId())
                .orElseThrow(() -> new MessagingException("Room not found."));
        if (session.getStatus() != Session.Status.ACTIVE) {
            throw new MessagingException("This room is not active.");
        }

        List<SessionPlayer> players = new ArrayList<>(
                playerRepository.findBySessionIdOrderByTotalScoreDesc(session.getId()));
        List<Map<String, Object>> leaderboard = new ArrayList<>();
        int rank = 1;
        for (SessionPlayer player : players) {
            player.setFinalRank(rank);
            playerRepository.save(player);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("nickname", player.getNickname());
            row.put("score", player.getTotalScore());
            row.put("rank", rank);
            row.put("isHost", player.getIsHost());
            leaderboard.add(row);
            rank++;
        }

        session.setStatus(Session.Status.ENDED);
        session.setEndedAt(Instant.now());
        sessionRepository.save(session);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "ROOM_FINISHED");
        event.put("leaderboard", leaderboard);
        broadcast(session, event);
    }

    /* ---------------------------------------------------------------- */

    private Session requireRoom(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            throw new MessagingException("roomCode is required.");
        }
        return sessionRepository.findByRoomCode(roomCode.trim().toUpperCase())
                .orElseThrow(() -> new MessagingException("Room not found."));
    }

    private Session requireHost(String roomCode, Principal principal) {
        Session session = requireRoom(roomCode);
        if (principal == null) {
            throw new MessagingException("Authentication required.");
        }
        if (!session.getHostId().toString().equals(principal.getName())) {
            throw new MessagingException("Only the host can do that.");
        }
        return session;
    }

    private SessionPlayer requirePlayer(Session session, Principal principal) {
        if (principal == null) {
            throw new MessagingException("Authentication required.");
        }
        UUID userId;
        try {
            userId = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException ex) {
            throw new MessagingException("Authentication required.");
        }
        return playerRepository.findBySessionIdAndUserId(session.getId(), userId)
                .orElseThrow(() -> new MessagingException("You are not in this room."));
    }

    private void broadcast(Session session, Map<String, Object> event) {
        messagingTemplate.convertAndSend("/topic/room/" + session.getRoomCode(), (Object) event);
    }

    private Map<String, Object> broadcastQuestion(Session session, Question question,
            Integer questionIndex, Long endsAt) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "QUESTION");
        event.put("index", questionIndex);
        event.put("id", question.getId().toString());
        event.put("text", question.getText());
        event.put("questionType", question.getType().name());
        if (question.getOptions() != null) {
            event.put("options", question.getOptions());
        }
        event.put("timeLimitSeconds", question.getTimeLimitSeconds());
        if (endsAt != null) {
            event.put("endsAt", endsAt);
        }
        // Intentionally no correctAnswer and no explanation until the reveal.
        broadcast(session, event);
        return event;
    }

    public record ReadyPayload(String roomCode, Boolean isReady) {}

    public record StartPayload(String roomCode) {}

    public record NextQuestionPayload(String roomCode, Integer questionIndex, UUID questionId, Long endsAt) {}

    public record AnswerPayload(String roomCode, UUID questionId) {}

    public record EndPayload(String roomCode) {}
}
