package com.kaushiksridhar.finledger.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.kaushiksridhar.finledger.budget.BudgetAlertService;
import com.kaushiksridhar.finledger.recurring.RecurringService;

/**
 * Re-checks a user's budgets (and optionally rescans for repeating payments) in a database
 * transaction of its own. Failures are logged and swallowed: an alert going wrong must never
 * turn the user's successful save or import into an error.
 */
@Component
public class AlertRefresher {

    private static final Logger log = LoggerFactory.getLogger(AlertRefresher.class);

    private final BudgetAlertService budgetAlertService;
    private final RecurringService recurringService;
    private final TransactionTemplate newTransaction;

    public AlertRefresher(BudgetAlertService budgetAlertService,
            RecurringService recurringService,
            PlatformTransactionManager transactionManager) {
        this.budgetAlertService = budgetAlertService;
        this.recurringService = recurringService;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void refresh(long userId, boolean rescanRecurring) {
        try {
            newTransaction.executeWithoutResult(status -> {
                budgetAlertService.checkCurrentMonth(userId);
                if (rescanRecurring) {
                    recurringService.scan(userId);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Couldn't update alerts for user {}", userId, e);
        }
    }
}
