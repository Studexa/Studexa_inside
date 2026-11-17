package com.studexa.ai;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiPropertiesTest {
    @Test
    void geminiRequiresBothExplicitEnablementAndAKey() {
        assertThat(properties(false, "key").geminiConfigured()).isFalse();
        assertThat(properties(true, " ").geminiConfigured()).isFalse();
        assertThat(properties(true, "key").geminiConfigured()).isTrue();
    }

    private AiProperties properties(boolean enabled, String key) {
        return new AiProperties(null, "model", 10, 12000, Duration.ofSeconds(5), key, "model", enabled);
    }
}
