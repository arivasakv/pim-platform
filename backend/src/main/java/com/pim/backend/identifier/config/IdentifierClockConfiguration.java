package com.pim.backend.identifier.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The clock that stamps {@code registeredAt} on new registrations; tests use a fixed clock instead. */
@Configuration
public class IdentifierClockConfiguration {

    @Bean
    Clock identifierClock() {
        return Clock.systemUTC();
    }
}
