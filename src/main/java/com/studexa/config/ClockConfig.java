package com.studexa.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single, injectable {@link Clock}. Services depend on this instead of
 * calling {@code Instant.now()} directly so time can be controlled in tests.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
