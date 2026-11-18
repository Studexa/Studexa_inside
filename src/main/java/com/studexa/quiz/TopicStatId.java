package com.studexa.quiz;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TopicStatId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID userId;
    private String subject;
    private String topic;

    public TopicStatId() {}

    public TopicStatId(UUID userId, String subject, String topic) {
        this.userId = userId;
        this.subject = subject;
        this.topic = topic;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TopicStatId)) return false;
        TopicStatId that = (TopicStatId) o;
        return Objects.equals(userId, that.userId)
                && Objects.equals(subject, that.subject)
                && Objects.equals(topic, that.topic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, subject, topic);
    }
}
