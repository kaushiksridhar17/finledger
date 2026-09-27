package com.kaushiksridhar.finledger.alerts;

import java.sql.Date;
import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.kaushiksridhar.finledger.budget.BudgetAlertService;
import com.kaushiksridhar.finledger.common.AppTime;
import com.kaushiksridhar.finledger.recurring.RecurringService;

/**
 * Every night at 2:30 in Indian time, for everyone with recent activity: rescan for repeating payments
 * (which also sends "due in 3 days" reminders) and re-check budgets. Each user runs in their own
 * transaction, so one user's problem can't stop everyone else's alerts.
 */
@Component
public class NightlyJobs {

    private static final Logger log = LoggerFactory.getLogger(NightlyJobs.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final RecurringService recurringService;
    private final BudgetAlertService budgetAlertService;
    private final Clock clock;

    public NightlyJobs(JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            RecurringService recurringService,
            BudgetAlertService budgetAlertService,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.recurringService = recurringService;
        this.budgetAlertService = budgetAlertService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Kolkata")
    public void runNightly() {
        List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT user_id FROM transactions WHERE txn_date >= ?",
                Long.class,
                Date.valueOf(AppTime.today(clock).minusDays(400)));

        int failed = 0;
        for (Long userId : userIds) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    recurringService.scan(userId);
                    budgetAlertService.checkCurrentMonth(userId);
                });
            } catch (RuntimeException e) {
                failed++;
                log.warn("Nightly job failed for user {}", userId, e);
            }
        }
        log.info("Nightly job checked {} user(s), {} failed", userIds.size(), failed);
    }
}
