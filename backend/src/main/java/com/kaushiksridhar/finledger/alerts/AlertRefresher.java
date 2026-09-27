package com.kaushiksridhar.finledger.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.kaushiksridhar.finledger.budget.BudgetAlertService;
import com.kaushiksridhar.finledger.recurring.RecurringService;
import com.kaushiksridhar.finledger.split.PaymentMatchService;

/**
 * After the ledger changes: re-checks the user's budgets (and optionally rescans for repeating payments),
 * then looks for friends paying the user back. Each runs in a database transaction of its own, and
 * failures are logged and swallowed: an alert going wrong must never turn the user's successful save
 * or import into an error, and one check failing doesn't stop the other.
 */
@Component
public class AlertRefresher {

    private static final Logger log = LoggerFactory.getLogger(AlertRefresher.class);

    private final BudgetAlertService budgetAlertService;
    private final RecurringService recurringService;
    private final PaymentMatchService paymentMatchService;
    private final TransactionTemplate newTransaction;

    public AlertRefresher(BudgetAlertService budgetAlertService,
            RecurringService recurringService,
            PaymentMatchService paymentMatchService,
            PlatformTransactionManager transactionManager) {
        this.budgetAlertService = budgetAlertService;
        this.recurringService = recurringService;
        this.paymentMatchService = paymentMatchService;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void refresh(long userId, boolean rescanRecurring) {
        runSafely(userId, "alerts", () -> {
            budgetAlertService.checkCurrentMonth(userId);
            if (rescanRecurring) {
                recurringService.scan(userId);
            }
        });
        runSafely(userId, "repayment matches", () -> paymentMatchService.scan(userId));
    }

    private void runSafely(long userId, String what, Runnable work) {
        try {
            newTransaction.executeWithoutResult(status -> work.run());
        } catch (RuntimeException e) {
            log.warn("Couldn't update {} for user {}", what, userId, e);
        }
    }
}
