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
import com.studexa.quiz.dto.QuizDto.CreateSessionRequest;
import com.studexa.quiz.dto.QuizDto.JoinRoomRequest;

import jakarta.validation.Valid;

/**
 * Session (room) management: create rooms, join by code, start games, leaderboard.
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionRepository sessionRepository;
    private final SessionPlayerRepository playerRepository;
    private final QuizRepository quizRepository;

    public SessionController(SessionRepository sessionRepository,
            SessionPlayerRepository playerRepository, QuizRepository quizRepository) {
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.quizRepository = quizRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDto.SessionResponse createSession(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSessionRequest request) {
        UUID hostId = CurrentUser.id(jwt);
        Quiz quiz = quizRepository.findById(request.quizId())
                .orElseThrow(() -> new RuntimeException("Quiz not found"));
        String roomCode = generateRoomCode();
        Session session = new Session(
                quiz.getId(), hostId, roomCode,
                Session.Mode.valueOf(request.mode()),
                "{\"numQuestions\":" + (request.numQuestions() != null ? request.numQuestions() : 10)
                        + ",\"timePerQuestion\":" + (request.timePerQuestion() != null ? request.timePerQuestion() : 30)
                        + ",\"difficulty\":\"" + (request.difficulty() != null ? request.difficulty() : "medium") + "\"}");
        session = sessionRepository.save(session);

        // Host auto-joins as a player
        SessionPlayer hostPlayer = new SessionPlayer(
                session.getId(), hostId, "Host", null, null, true);
        hostPlayer.setIsReady(true);
        playerRepository.save(hostPlayer);

        return toResponse(session);
    }

    @GetMapping("/room/{code}")
    public QuizDto.RoomInfoResponse getRoom(@PathVariable String code) {
        Session session = sessionRepository.findByRoomCode(code)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        List<QuizDto.SessionPlayerResponse> players = playerRepository.findBySessionId(session.getId()).stream()
                .map(this::toResponse)
                .toList();
        return new QuizDto.RoomInfoResponse(toResponse(session), players);
    }

    @PostMapping("/{id}/start")
    public QuizDto.SessionResponse startSession(@PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.id(jwt);
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getHostId().equals(userId)) {
            throw new RuntimeException("Only the host can start the session");
        }
        session.setStatus(Session.Status.ACTIVE);
        session.setStartedAt(java.time.Instant.now());
        session = sessionRepository.save(session);
        return toResponse(session);
    }

    @GetMapping("/{id}/players")
    public List<QuizDto.SessionPlayerResponse> getPlayers(@PathVariable UUID id) {
        return playerRepository.findBySessionIdOrderByTotalScoreDesc(id).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/{id}/players")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDto.SessionPlayerResponse joinSession(@PathVariable UUID id,
            @Valid @RequestBody JoinRoomRequest request) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        if (playerRepository.existsBySessionIdAndNickname(id, request.nickname())) {
            throw new RuntimeException("Nickname already taken in this room");
        }
        SessionPlayer player = new SessionPlayer(
                id, null, request.nickname(), null, null, false);
        player = playerRepository.save(player);
        return toResponse(player);
    }

    @GetMapping("/{id}/leaderboard")
    public List<QuizDto.LeaderboardEntry> getLeaderboard(@PathVariable UUID id) {
        return playerRepository.findBySessionIdOrderByTotalScoreDesc(id).stream()
                .map(p -> new QuizDto.LeaderboardEntry(
                        p.getNickname(), p.getTotalScore(), p.getFinalRank(), p.getIsHost()))
                .toList();
    }

    private QuizDto.SessionResponse toResponse(Session s) {
        return new QuizDto.SessionResponse(
                s.getId(), s.getQuizId(), s.getHostId(), s.getRoomCode(),
                s.getMode().name(), s.getStatus().name(), s.getSettings(),
                s.getStartedAt(), s.getEndedAt(), s.getCreatedAt());
    }

    private QuizDto.SessionPlayerResponse toResponse(SessionPlayer p) {
        return new QuizDto.SessionPlayerResponse(
                p.getId(), p.getSessionId(), p.getUserId(), p.getNickname(),
                p.getAvatarUrl(), p.getTeamId(), p.getTotalScore(),
                p.getFinalRank(), p.getIsReady(), p.getIsHost());
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(6);
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
