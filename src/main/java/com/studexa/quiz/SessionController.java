package com.studexa.quiz;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import com.studexa.common.ApiException;
import com.studexa.common.CurrentUser;
import com.studexa.quiz.dto.QuizDto;
import com.studexa.quiz.dto.QuizDto.CreateSessionRequest;
import com.studexa.quiz.dto.QuizDto.JoinRoomRequest;
import com.studexa.user.ProfileRepository;

import jakarta.validation.Valid;

/**
 * Session (room) management: create rooms, join by code, start games, leaderboard.
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    /** Room-code alphabet without look-alike characters (no 0/O, 1/I/L). */
    private static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ROOM_CODE_LENGTH = 6;

    private final SessionRepository sessionRepository;
    private final SessionPlayerRepository playerRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final LiveQuizService liveQuiz;
    private final ProfileRepository profiles;
    private final ApplicationEventPublisher events;

    public SessionController(SessionRepository sessionRepository,
            SessionPlayerRepository playerRepository,
            QuizRepository quizRepository,
            QuestionRepository questionRepository,
            ObjectMapper objectMapper, LiveQuizService liveQuiz, ProfileRepository profiles, ApplicationEventPublisher events) {
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
        this.liveQuiz = liveQuiz;
        this.profiles = profiles;
        this.events = events;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public QuizDto.SessionResponse createSession(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSessionRequest request) {
        UUID hostId = CurrentUser.id(jwt);
        if (request.quizId() == null) {
            throw ApiException.badRequest("QUIZ_REQUIRED", "Create a quiz before opening a room.");
        }
        Quiz quiz = quizRepository.findByIdAndOwnerId(request.quizId(), hostId)
                .orElseThrow(() -> ApiException.notFound("Quiz not found."));

        Session session = new Session(
                quiz.getId(), hostId, nextRoomCode(),
                mode(request.mode()),
                settingsJson(request));
        session = sessionRepository.save(session);

        // Host auto-joins as a player so the lobby always has at least one member.
        String nickname = profiles.findById(hostId).map(profile -> profile.getNickname()).orElse("Host");
        SessionPlayer hostPlayer = new SessionPlayer(session.getId(), hostId, nickname, null, null, true);
        hostPlayer.setIsReady(true);
        playerRepository.save(hostPlayer);

        return toResponse(session);
    }

    @GetMapping("/room/{code}")
    public QuizDto.RoomInfoResponse getRoom(@PathVariable String code) {
        Session session = sessionRepository.findByRoomCode(code.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Room not found."));
        List<QuizDto.SessionPlayerResponse> players = playerRepository.findBySessionId(session.getId()).stream()
                .map(this::toResponse)
                .toList();
        String title = quizRepository.findById(session.getQuizId()).orElseThrow(() -> ApiException.notFound("Quiz not found.")).getTitle();
        int count = liveQuiz.questionCount(session, questionRepository.countByQuizId(session.getQuizId()));
        return new QuizDto.RoomInfoResponse(toResponse(session), players, title, count, liveQuiz.roomTime(session));
    }

    @PostMapping("/{id}/start")
    public QuizDto.SessionResponse startSession(@PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return toResponse(liveQuiz.start(id, CurrentUser.id(jwt)));
    }

    @GetMapping("/{id}/state")
    public QuizDto.LiveState getState(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return liveQuiz.state(id, CurrentUser.id(jwt));
    }

    @PostMapping("/{id}/advance")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void advance(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @RequestBody QuizDto.AdvanceRequest request) {
        liveQuiz.advance(id, CurrentUser.id(jwt), request.questionId());
    }

    @GetMapping("/{id}/players")
    public List<QuizDto.SessionPlayerResponse> getPlayers(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        liveQuiz.requireMember(id, CurrentUser.id(jwt));
        return playerRepository.findBySessionIdOrderByTotalScoreDescIdAsc(id).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Join by session id (the lobby resolves the 6-character code first).
     * Re-joining with the same account returns the existing player, which is
     * what a reconnect needs, instead of failing with a duplicate nickname.
     */
    @PostMapping("/{id}/players")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public QuizDto.SessionPlayerResponse joinSession(@PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody JoinRoomRequest request) {
        UUID userId = CurrentUser.id(jwt);
        Session session = sessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Room not found."));
        if (!session.getRoomCode().equalsIgnoreCase(request.roomCode() == null ? "" : request.roomCode().trim())) {
            throw ApiException.notFound("Room not found.");
        }

        SessionPlayer existing = playerRepository.findBySessionIdAndUserId(id, userId).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        if (session.getStatus() != Session.Status.WAITING) {
            throw ApiException.conflict("ROOM_CLOSED", "This room is no longer accepting players.");
        }

        String nickname = request.nickname() == null ? "" : request.nickname().trim();
        if (nickname.isEmpty()) {
            throw ApiException.badRequest("NICKNAME_REQUIRED", "Enter a nickname to join.");
        }
        if (nickname.length() > 50) {
            throw ApiException.badRequest("INVALID_NICKNAME", "Nickname must be 50 characters or fewer.");
        }
        if (playerRepository.existsBySessionIdAndNickname(id, nickname)) {
            throw ApiException.conflict("NICKNAME_TAKEN", "That nickname is already taken in this room.");
        }

        SessionPlayer player = new SessionPlayer(
                id, userId, nickname, null, null, false);
        player = playerRepository.save(player);
        events.publishEvent(new LiveQuizService.RoomChanged(session.getRoomCode()));
        return toResponse(player);
    }

    @GetMapping("/{id}/leaderboard")
    public List<QuizDto.LeaderboardEntry> getLeaderboard(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        liveQuiz.requireMember(id, CurrentUser.id(jwt));
        sessionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Session not found."));
        return playerRepository.findBySessionIdOrderByTotalScoreDescIdAsc(id).stream()
                .map(p -> new QuizDto.LeaderboardEntry(
                        p.getNickname(), p.getTotalScore(), p.getFinalRank(), p.getIsHost()))
                .toList();
    }

    private String nextRoomCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = randomRoomCode();
            if (sessionRepository.findByRoomCode(code).isEmpty()) {
                return code;
            }
        }
        throw ApiException.serviceUnavailable("ROOM_CODE_UNAVAILABLE",
                "Could not allocate a room code. Please try again.");
    }

    private static String randomRoomCode() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            sb.append(ROOM_CODE_ALPHABET.charAt(random.nextInt(ROOM_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static Session.Mode mode(String value) {
        if (value == null || value.isBlank()) {
            return Session.Mode.LIVE_RACE;
        }
        try {
            return Session.Mode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_MODE", "Unknown quiz mode.");
        }
    }

    private String settingsJson(CreateSessionRequest request) {
        int questionCount = request.numQuestions() != null ? request.numQuestions() : 10;
        int timePerQuestion = request.timePerQuestion() != null ? request.timePerQuestion() : 30;
        if (questionCount < 1 || questionCount > 50) {
            throw ApiException.badRequest("INVALID_QUESTION_COUNT", "Question count must be between 1 and 50.");
        }
        if (timePerQuestion < 5 || timePerQuestion > 600) {
            throw ApiException.badRequest("INVALID_TIME_LIMIT", "Question time must be between 5 and 600 seconds.");
        }
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("numQuestions", questionCount);
        settings.put("timePerQuestion", timePerQuestion);
        settings.put("difficulty", request.difficulty() != null ? request.difficulty() : "medium");
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JacksonException ex) {
            throw ApiException.badRequest("INVALID_SETTINGS", "Could not read the room settings.");
        }
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
}
