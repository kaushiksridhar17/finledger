package com.kaushiksridhar.finledger.demo;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.user.UserRepository;

/** Every hour, deletes demo users whose time is up (and, through the foreign keys, all their data). */
@Component
public class DemoCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(DemoCleanupJob.class);

    private final UserRepository userRepository;
    private final Clock clock;

    public DemoCleanupJob(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    // First run one minute after startup, then one hour after each run finishes
    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    @Transactional
    public void deleteExpiredDemoUsers() {
        int deleted = userRepository.deleteExpiredDemoUsers(clock.instant());
        if (deleted > 0) {
            log.info("Deleted {} expired demo user(s)", deleted);
        }
    }
}
