package com.studexa.quiz;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.quiz.dto.QuizDto;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class LiveQuizService {
    private final SessionRepository sessions;
    private final QuestionRepository questions;
    private final SessionPlayerRepository players;
    private final AnswerRepository answers;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;

    public LiveQuizService(SessionRepository sessions, QuestionRepository questions, SessionPlayerRepository players,
            AnswerRepository answers, ObjectMapper json, ApplicationEventPublisher events) {
        this.sessions = sessions;
        this.questions = questions;
        this.players = players;
        this.answers = answers;
        this.json = json;
        this.events = events;
    }

    @Transactional
    public Session start(UUID sessionId, UUID owner) {
        Session session = lockHost(sessionId, owner);
        if (session.getStatus() == Session.Status.ENDED) {
            throw ApiException.conflict("SESSION_ENDED", "This room has already finished.");
        }
        if (session.getCurrentQuestionId() != null) return session;
        List<Question> ordered = orderedQuestions(session);
        if (ordered.isEmpty()) {
            throw ApiException.badRequest("QUIZ_HAS_NO_QUESTIONS", "Add at least one question before starting this room.");
        }
        Instant now = Instant.now();
        if (session.getStartedAt() == null) session.setStartedAt(now);
        session.setStatus(Session.Status.ACTIVE);
        openQuestion(session, ordered.getFirst(), now);
        return session;
    }

    @Transactional(readOnly = true)
    public QuizDto.LiveState state(UUID sessionId, UUID user) {
        Session session = sessions.findById(sessionId).orElseThrow(LiveQuizService::roomNotFound);
        SessionPlayer player = requireMember(sessionId, user);
        List<Question> ordered = orderedQuestions(session);
        int index = currentIndex(ordered, session.getCurrentQuestionId());
        QuizDto.LiveQuestion current = null;
        QuizDto.AnswerResponse ownAnswer = null;
        int answeredCount = 0;
        List<UUID> playerIds = playerIds(sessionId);
        if (session.getStatus() == Session.Status.ACTIVE && index >= 0 && session.getCurrentQuestionStartedAt() != null) {
            Question question = ordered.get(index);
            current = new QuizDto.LiveQuestion(question.getId(), question.getText(), visibleOptions(question), index + 1,
                    timeLimit(question), endsAt(session, question).toEpochMilli());
            ownAnswer = answers.findBySessionPlayerIdAndQuestionId(player.getId(), question.getId()).stream()
                    .findFirst().map(answer -> new QuizDto.AnswerResponse(answer.getId(), answer.getIsCorrect(),
                            answer.getScoreAwarded(), question.getCorrectAnswer(), question.getExplanation())).orElse(null);
            answeredCount = (int) answers.countForQuestion(question.getId(), playerIds);
        }
        return new QuizDto.LiveState(session.getStatus().name(), session.getRoomCode(), session.getHostId().equals(user),
                player.getId(), current, ownAnswer, answeredCount, playerIds.size(), ordered.size(), System.currentTimeMillis());
    }

    @Transactional
    public void advance(UUID sessionId, UUID owner, UUID expectedQuestionId) {
        if (expectedQuestionId == null) throw ApiException.badRequest("QUESTION_REQUIRED", "Choose the active question.");
        Session session = lockHost(sessionId, owner);
        if (session.getStatus() != Session.Status.ACTIVE || !expectedQuestionId.equals(session.getCurrentQuestionId())) return;
        List<Question> ordered = orderedQuestions(session);
        int index = currentIndex(ordered, expectedQuestionId);
        if (index >= 0) advance(session, ordered, index);
    }

    @Transactional
    public void advanceTo(UUID sessionId, UUID owner, UUID nextQuestionId) {
        Session session = lockHost(sessionId, owner);
        if (session.getStatus() != Session.Status.ACTIVE || nextQuestionId.equals(session.getCurrentQuestionId())) return;
        List<Question> ordered = orderedQuestions(session);
        int index = currentIndex(ordered, session.getCurrentQuestionId());
        if (index >= 0 && index + 1 < ordered.size() && nextQuestionId.equals(ordered.get(index + 1).getId())) {
            advance(session, ordered, index);
        }
    }

    @Transactional
    public void finish(UUID sessionId, UUID owner) {
        Session session = lockHost(sessionId, owner);
        if (session.getStatus() != Session.Status.ACTIVE) return;
        List<Question> ordered = orderedQuestions(session);
        int index = currentIndex(ordered, session.getCurrentQuestionId());
        if (index >= 0 && index == ordered.size() - 1) advance(session, ordered, index);
    }

    SessionPlayer requireMember(UUID sessionId, UUID user) {
        return players.findBySessionIdAndUserId(sessionId, user).orElseThrow(LiveQuizService::roomNotFound);
    }

    int questionCount(Session session, int available) {
        return Math.min(available, setting(session, "numQuestions", available, 1, 50));
    }

    int roomTime(Session session) {
        return setting(session, "timePerQuestion", 30, 5, 600);
    }

    private int setting(Session session, String key, int fallback, int minimum, int maximum) {
        try {
            JsonNode value = json.readTree(session.getSettings()).path(key);
            return value.isIntegralNumber() ? Math.max(minimum, Math.min(maximum, value.asInt())) : fallback;
        } catch (JacksonException ex) {
            return fallback;
        }
    }

    private List<Question> orderedQuestions(Session session) {
        List<Question> ordered = questions.findByQuizIdOrderByCreatedAtAscIdAsc(session.getQuizId());
        return ordered.subList(0, questionCount(session, ordered.size()));
    }

    private String visibleOptions(Question question) {
        if (question.getOptions() == null) return null;
        try {
            JsonNode options = json.readTree(question.getOptions());
            if (!options.isArray()) return null;
            var visible = json.createArrayNode();
            for (JsonNode option : options) {
                if (option.isTextual()) {
                    visible.add(option.asText());
                } else if (option.hasNonNull("text")) {
                    var choice = visible.addObject().put("text", option.get("text").asText());
                    if (option.hasNonNull("id")) choice.put("id", option.get("id").asText());
                }
            }
            return json.writeValueAsString(visible);
        } catch (JacksonException ex) {
            return null;
        }
    }

    private void advance(Session session, List<Question> ordered, int index) {
        Instant now = Instant.now();
        Question current = ordered.get(index);
        if (now.isBefore(endsAt(session, current))) {
            List<UUID> ids = playerIds(session.getId());
            if (answers.countForQuestion(current.getId(), ids) < ids.size()) {
                throw ApiException.conflict("QUESTION_IN_PROGRESS", "Wait for the remaining players or the timer.");
            }
        }
        if (index + 1 < ordered.size()) {
            openQuestion(session, ordered.get(index + 1), now);
            return;
        }
        List<SessionPlayer> ranked = players.findBySessionIdOrderByTotalScoreDescIdAsc(session.getId());
        for (int i = 0; i < ranked.size(); i++) ranked.get(i).setFinalRank(i + 1);
        session.setStatus(Session.Status.ENDED);
        session.setEndedAt(now);
        events.publishEvent(new RoomChanged(session.getRoomCode()));
    }

    private void openQuestion(Session session, Question question, Instant now) {
        session.setCurrentQuestionId(question.getId());
        session.setCurrentQuestionStartedAt(now);
        events.publishEvent(new RoomChanged(session.getRoomCode()));
    }

    private Session lockHost(UUID id, UUID owner) {
        return sessions.findByIdForUpdate(id).filter(session -> session.getHostId().equals(owner))
                .orElseThrow(LiveQuizService::roomNotFound);
    }

    private List<UUID> playerIds(UUID sessionId) {
        return players.findBySessionId(sessionId).stream().map(SessionPlayer::getId).toList();
    }

    private static int currentIndex(List<Question> ordered, UUID id) {
        for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).getId().equals(id)) return i;
        return -1;
    }

    static int timeLimit(Question question) {
        Integer seconds = question.getTimeLimitSeconds();
        return seconds == null ? 30 : Math.max(1, Math.min(600, seconds));
    }

    private static Instant endsAt(Session session, Question question) {
        return session.getCurrentQuestionStartedAt().plusSeconds(timeLimit(question));
    }

    private static ApiException roomNotFound() {
        return ApiException.notFound("Room not found.");
    }

    public record RoomChanged(String roomCode) {}
}
