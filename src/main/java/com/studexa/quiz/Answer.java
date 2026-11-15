package com.studexa.quiz;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An answer given by a player during a session.
 */
@Entity
@Table(name = "answers")
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_player_id", nullable = false)
    private UUID sessionPlayerId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(nullable = false)
    private String selectedAnswer;

    @Column(nullable = false)
    private Boolean isCorrect;

    @Column(name = "response_time_ms", nullable = false)
    private Integer responseTimeMs;

    @Column(nullable = false)
    private Integer scoreAwarded = 0;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    public Answer() {}

    public Answer(UUID sessionPlayerId, UUID questionId, String selectedAnswer,
            Boolean isCorrect, Integer responseTimeMs, Integer scoreAwarded) {
        this.sessionPlayerId = sessionPlayerId;
        this.questionId = questionId;
        this.selectedAnswer = selectedAnswer;
        this.isCorrect = isCorrect;
        this.responseTimeMs = responseTimeMs;
        this.scoreAwarded = scoreAwarded != null ? scoreAwarded : 0;
        this.answeredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSessionPlayerId() { return sessionPlayerId; }
    public void setSessionPlayerId(UUID sessionPlayerId) { this.sessionPlayerId = sessionPlayerId; }
    public UUID getQuestionId() { return questionId; }
    public void setQuestionId(UUID questionId) { this.questionId = questionId; }
    public String getSelectedAnswer() { return selectedAnswer; }
    public void setSelectedAnswer(String selectedAnswer) { this.selectedAnswer = selectedAnswer; }
    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }
    public Integer getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Integer responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    public Integer getScoreAwarded() { return scoreAwarded; }
    public void setScoreAwarded(Integer scoreAwarded) { this.scoreAwarded = scoreAwarded; }
    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant answeredAt) { this.answeredAt = answeredAt; }
}
