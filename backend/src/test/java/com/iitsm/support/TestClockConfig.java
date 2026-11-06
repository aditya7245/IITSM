package com.iitsm.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

/** Replaces the real clock with a {@link MutableClock} in integration tests. */
@TestConfiguration
public class TestClockConfig {

    public static final Instant START = Instant.parse("2026-11-10T09:00:00Z");

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(START);
    }
}
