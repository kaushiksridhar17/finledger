package com.kaushiksridhar.finledger.demo;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * finledger.demo.* settings.
 * enabled   - show and allow "Try the demo"
 * ttl       - how long a demo user lives before the cleanup job deletes it
 * maxActive - cap on live demo users, so the button can't be used to fill the database
 */
@ConfigurationProperties(prefix = "finledger.demo")
public record DemoProperties(
        boolean enabled,
        Duration ttl,
        int maxActive) {
}
