package com.kaushiksridhar.finledger.dashboard;

import java.util.List;

import com.kaushiksridhar.finledger.transaction.TransactionResponse;

/**
 * Everything the dashboard shows, in one request.
 *
 * month              - the month being viewed, e.g. "2026-09"
 * netWorthPaise      - all active account balances added up, as of now
 * incomePaise        - money earned in the month (transfers excluded)
 * spendingPaise      - money spent in the month (transfers excluded)
 * monthly            - the 12 months ending with this one, oldest first, zeros for empty months
 * spendingByCategory - where this month's spending went, biggest first
 * recent             - the 5 newest transactions
 */
public record DashboardResponse(
        String month,
        long netWorthPaise,
        long incomePaise,
        long spendingPaise,
        List<MonthSummary> monthly,
        List<CategorySpend> spendingByCategory,
        List<TransactionResponse> recent) {

    public record MonthSummary(String month, long incomePaise, long spendingPaise) {
    }

    public record CategorySpend(Long categoryId, String name, String color, long amountPaise) {
    }
}
