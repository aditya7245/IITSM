package com.iitsm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The single source of "now" for business code. Inject {@link Clock} instead of calling Instant.now(),
 * so tests can replace it with a controllable clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
