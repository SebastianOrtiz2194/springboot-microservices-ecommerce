package com.ecommerce.product.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides an injectable {@link Clock} so time-dependent logic is testable. */
@Configuration
public class ClockConfig {

    /**
     * System UTC clock used by maintenance jobs.
     *
     * @return the application clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
