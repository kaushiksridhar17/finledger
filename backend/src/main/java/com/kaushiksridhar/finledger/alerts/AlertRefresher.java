package com.kaushiksridhar.finledger.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.kaushiksridhar.finledger.budget.BudgetAlertService;
import com.kaushiksridhar.finledger.investment.InvestmentService;
import com.kaushiksridhar.finledger.recurring.RecurringService;
import com.kaushiksridhar.finledger.split.PaymentMatchService;

/**
 * After the ledger changes: re-checks the user's budgets (and optionally rescans for repeating payments),
 * looks for friends paying the user back, and turns new SIP debits into fund purchases.
 * Each runs in a database transaction of its own, and failures are logged and swallowed: an alert going
 * wrong must never turn the user's successful save or import into an error, and one check failing
 * doesn't stop the others.
 */
@Component
public class AlertRefresher {

    private static final Logger log = LoggerFactory.getLogger(AlertRefresher.class);

    private final BudgetAlertService budgetAlertService;
    private final RecurringService recurringService;
    private final PaymentMatchService paymentMatchService;
    private final InvestmentService investmentService;
    private final TransactionTemplate newTransaction;

    public AlertRefresher(BudgetAlertService budgetAlertService,
            RecurringService recurringService,
            PaymentMatchService paymentMatchService,
            InvestmentService investmentService,
            PlatformTransactionManager transactionManager) {
        this.budgetAlertService = budgetAlertService;
        this.recurringService = recurringService;
        this.paymentMatchService = paymentMatchService;
        this.investmentService = investmentService;
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
        runSafely(userId, "SIP purchases", () -> investmentService.syncSips(userId));
    }

    private void runSafely(long userId, String what, Runnable work) {
        try {
            newTransaction.executeWithoutResult(status -> work.run());
        } catch (RuntimeException e) {
            log.warn("Couldn't update {} for user {}", what, userId, e);
        }
    }
}
