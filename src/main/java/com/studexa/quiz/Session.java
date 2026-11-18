package com.studexa.quiz;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A live game session (a room). Players join by room_code.
 */
@Entity
@Table(name = "sessions")
public class Session {

    public enum Mode { LIVE_RACE, TEAM, CO_OP, SELF_PACED, SOLO_PRACTICE }
    public enum Status { WAITING, ACTIVE, ENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(nullable = false, length = 6, unique = true)
    private String roomCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Mode mode = Mode.LIVE_RACE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.WAITING;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String settings = "{}"; // numQuestions, timePerQuestion, difficulty, etc.

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "current_question_id")
    private UUID currentQuestionId;

    @Column(name = "current_question_started_at")
    private Instant currentQuestionStartedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Session() {}

    public Session(UUID quizId, UUID hostId, String roomCode, Mode mode, String settings) {
        this.quizId = quizId;
        this.hostId = hostId;
        this.roomCode = roomCode;
        this.mode = mode;
        this.status = Status.WAITING;
        this.settings = settings != null ? settings : "{}";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getQuizId() { return quizId; }
    public void setQuizId(UUID quizId) { this.quizId = quizId; }
    public UUID getHostId() { return hostId; }
    public void setHostId(UUID hostId) { this.hostId = hostId; }
    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getSettings() { return settings; }
    public void setSettings(String settings) { this.settings = settings; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public UUID getCurrentQuestionId() { return currentQuestionId; }
    public void setCurrentQuestionId(UUID currentQuestionId) { this.currentQuestionId = currentQuestionId; }
    public Instant getCurrentQuestionStartedAt() { return currentQuestionStartedAt; }
    public void setCurrentQuestionStartedAt(Instant currentQuestionStartedAt) { this.currentQuestionStartedAt = currentQuestionStartedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
