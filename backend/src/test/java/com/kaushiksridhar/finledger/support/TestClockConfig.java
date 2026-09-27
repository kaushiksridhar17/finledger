package com.kaushiksridhar.finledger.support;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the real clock with a MutableClock in tests that @Import this class.
 * @Primary makes Spring inject this one wherever a Clock is needed.
 */
@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(Instant.now());
    }
}
