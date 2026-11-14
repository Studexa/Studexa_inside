package com.studexa.ai;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_usage")
public class AiUsage {

    @EmbeddedId
    private AiUsageId id;

    @Column(nullable = false)
    private int calls;

    public AiUsage() {
    }

    public static AiUsage start(AiUsageId id) {
        AiUsage usage = new AiUsage();
        usage.id = id;
        usage.calls = 0;
        return usage;
    }

    public AiUsageId getId() {
        return id;
    }

    public void setId(AiUsageId id) {
        this.id = id;
    }

    public int getCalls() {
        return calls;
    }

    public void setCalls(int calls) {
        this.calls = calls;
    }
}
