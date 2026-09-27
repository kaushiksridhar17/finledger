package com.kaushiksridhar.finledger.dashboard;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.account.AccountResponse;
import com.kaushiksridhar.finledger.account.AccountService;
import com.kaushiksridhar.finledger.category.CategoryKind;
import com.kaushiksridhar.finledger.dashboard.DashboardResponse.CategorySpend;
import com.kaushiksridhar.finledger.dashboard.DashboardResponse.MonthSummary;
import com.kaushiksridhar.finledger.transaction.TransactionFilter;
import com.kaushiksridhar.finledger.transaction.TransactionRepository;
import com.kaushiksridhar.finledger.transaction.TransactionRepository.CategoryTotalRow;
import com.kaushiksridhar.finledger.transaction.TransactionRepository.MonthlyTotalRow;
import com.kaushiksridhar.finledger.transaction.TransactionService;

@Service
public class DashboardService {

    static final int MONTHS_OF_HISTORY = 12;
    static final int RECENT_COUNT = 5;
    static final String UNCATEGORISED_NAME = "Uncategorised";
    static final String UNCATEGORISED_COLOR = "#94a3b8";

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final AccountService accountService;

    public DashboardService(TransactionRepository transactionRepository,
            TransactionService transactionService,
            AccountService accountService) {
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse get(long userId, YearMonth month) {
        List<MonthSummary> monthly = monthlyTotals(userId, month);
        MonthSummary current = monthly.get(monthly.size() - 1);

        List<CategorySpend> byCategory = transactionRepository
                .spendingByCategory(userId, month.atDay(1), month.atEndOfMonth(), CategoryKind.TRANSFER)
                .stream()
                .map(DashboardService::toCategorySpend)
                .toList();

        long netWorth = accountService.list(userId).stream()
                .filter(account -> !account.archived())
                .mapToLong(AccountResponse::balancePaise)
                .sum();

        var recent = transactionService.search(userId,
                new TransactionFilter(null, null, null, null, null, null), 0, RECENT_COUNT).items();

        return new DashboardResponse(month.toString(), netWorth,
                current.incomePaise(), current.spendingPaise(), monthly, byCategory, recent);
    }

    /** One entry per month, oldest first. The database only returns months with data, so the gaps are filled with zeros. */
    private List<MonthSummary> monthlyTotals(long userId, YearMonth lastMonth) {
        YearMonth firstMonth = lastMonth.minusMonths(MONTHS_OF_HISTORY - 1);

        Map<YearMonth, MonthlyTotalRow> rows = new HashMap<>();
        for (MonthlyTotalRow row : transactionRepository.monthlyTotals(
                userId, firstMonth.atDay(1), lastMonth.atEndOfMonth(), CategoryKind.TRANSFER)) {
            rows.put(YearMonth.of(row.getYr().intValue(), row.getMon().intValue()), row);
        }

        List<MonthSummary> result = new ArrayList<>(MONTHS_OF_HISTORY);
        for (YearMonth m = firstMonth; !m.isAfter(lastMonth); m = m.plusMonths(1)) {
            MonthlyTotalRow row = rows.get(m);
            result.add(row == null
                    ? new MonthSummary(m.toString(), 0, 0)
                    : new MonthSummary(m.toString(), row.getIncome().longValue(), row.getSpending().longValue()));
        }
        return result;
    }

    private static CategorySpend toCategorySpend(CategoryTotalRow row) {
        boolean uncategorised = row.getCategoryId() == null;
        return new CategorySpend(
                row.getCategoryId(),
                uncategorised ? UNCATEGORISED_NAME : row.getName(),
                uncategorised ? UNCATEGORISED_COLOR : row.getColor(),
                row.getTotal().longValue());
    }
}
