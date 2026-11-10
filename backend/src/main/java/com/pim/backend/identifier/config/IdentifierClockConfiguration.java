package com.pim.backend.identifier.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock that stamps {@code registeredAt} on new registrations (and {@code createdAt} on attributes and groups);
 * tests use a fixed clock instead. It ticks in whole milliseconds because MongoDB stores dates to the millisecond
 * (ADR 0003): a timestamp returned when something is created is then the same one read back later.
 */
@Configuration
public class IdentifierClockConfiguration {

    @Bean
    Clock identifierClock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }
}
