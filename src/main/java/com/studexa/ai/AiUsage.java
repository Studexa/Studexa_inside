package com.studexa.ai;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_usage")
@Getter
@Setter
@NoArgsConstructor
public class AiUsage {

    @EmbeddedId
    private AiUsageId id;

    @Column(nullable = false)
    private int calls;

    public static AiUsage start(AiUsageId id) {
        AiUsage usage = new AiUsage();
        usage.id = id;
        usage.calls = 0;
        return usage;
    }
}
