package com.kaushiksridhar.finledger.budget;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.common.AppTime;
import com.kaushiksridhar.finledger.common.MoneyText;
import com.kaushiksridhar.finledger.notification.NotificationService;
import com.kaushiksridhar.finledger.notification.NotificationType;

/**
 * Sends "you've used 80% of your Food budget" and "you've gone over" notifications.
 * Each alert has a dedupe key per budget, month and level, so it arrives once a month at most,
 * however many transactions are added afterwards.
 */
@Service
public class BudgetAlertService {

    private final BudgetService budgetService;
    private final NotificationService notificationService;
    private final Clock clock;

    public BudgetAlertService(BudgetService budgetService, NotificationService notificationService, Clock clock) {
        this.budgetService = budgetService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public void checkCurrentMonth(long userId) {
        YearMonth month = AppTime.thisMonth(clock);
        String monthName = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        for (BudgetResponse budget : budgetService.progress(userId, month)) {
            String keyPrefix = "budget:" + budget.id() + ":" + month + ":";

            if (budget.status() == BudgetStatus.OVER) {
                notificationService.notify(userId, NotificationType.BUDGET_EXCEEDED, keyPrefix + "100",
                        budget.categoryName() + " budget exceeded",
                        "You've spent " + MoneyText.format(budget.spentPaise()) + " of "
                                + MoneyText.format(budget.limitPaise()) + " in " + monthName + ".",
                        "/budgets");
            } else if (budget.status() == BudgetStatus.NEAR_LIMIT) {
                notificationService.notify(userId, NotificationType.BUDGET_WARNING, keyPrefix + "80",
                        budget.categoryName() + " budget at " + budget.percent() + "%",
                        MoneyText.format(budget.remainingPaise()) + " left of "
                                + MoneyText.format(budget.limitPaise()) + " for " + monthName + ".",
                        "/budgets");
            }
        }
    }
}
