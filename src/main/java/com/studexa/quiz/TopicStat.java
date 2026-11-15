package com.studexa.quiz;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Per-user topic weakness stats (cached for the dashboard).
 */
@Entity
@Table(name = "topic_stats")
@IdClass(TopicStatId.class)
public class TopicStat {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(length = 100)
    private String subject;

    @Id
    @Column(length = 100)
    private String topic;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(nullable = false)
    private Integer correct = 0;

    @Column(name = "avg_time_ms", nullable = false)
    private Integer avgTimeMs = 0;

    @Column(nullable = false)
    private Double weaknessScore = 1.0;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    public TopicStat() {}

    public TopicStat(UUID userId, String subject, String topic) {
        this.userId = userId;
        this.subject = subject;
        this.topic = topic;
        this.lastUpdated = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public Integer getCorrect() { return correct; }
    public void setCorrect(Integer correct) { this.correct = correct; }
    public Integer getAvgTimeMs() { return avgTimeMs; }
    public void setAvgTimeMs(Integer avgTimeMs) { this.avgTimeMs = avgTimeMs; }
    public Double getWeaknessScore() { return weaknessScore; }
    public void setWeaknessScore(Double weaknessScore) { this.weaknessScore = weaknessScore; }
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
}
