package com.kaushiksridhar.finledger.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled background jobs: the hourly demo cleanup, the 2:30 am bill and budget checks,
 * and the 11:40 pm NAV refresh (all Indian time).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
