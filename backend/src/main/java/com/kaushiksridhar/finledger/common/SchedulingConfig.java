package com.kaushiksridhar.finledger.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled background jobs (demo cleanup now; bill reminders and NAV updates later). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
