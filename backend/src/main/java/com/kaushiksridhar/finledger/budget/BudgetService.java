package com.kaushiksridhar.finledger.budget;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.budget.BudgetRepository.CategorySum;
import com.kaushiksridhar.finledger.category.Category;
import com.kaushiksridhar.finledger.category.CategoryKind;
import com.kaushiksridhar.finledger.category.CategoryService;
import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.common.AppTime;
import com.kaushiksridhar.finledger.transaction.TransactionRepository;
import com.kaushiksridhar.finledger.user.UserRepository;

@Service
public class BudgetService {

    static final int WARNING_PERCENT = 80;
    static final long MAX_LIMIT_PAISE = 100_000_000_000L;
    static final int SUGGESTION_MONTHS = 3;

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;
    private final UserRepository userRepository;
    private final Clock clock;

    public BudgetService(BudgetRepository budgetRepository,
            TransactionRepository transactionRepository,
            CategoryService categoryService,
            UserRepository userRepository,
            Clock clock) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
        this.categoryService = categoryService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Every budget with how much has been spent against it in the given month. */
    @Transactional(readOnly = true)
    public List<BudgetResponse> progress(long userId, YearMonth month) {
        List<Budget> budgets = budgetRepository.findByUserIdOrderByIdAsc(userId);
        if (budgets.isEmpty()) {
            return List.of();
        }

        List<Long> categoryIds = budgets.stream().map(b -> b.getCategory().getId()).toList();
        Map<Long, Long> netByCategory = budgetRepository
                .netByCategory(userId, categoryIds, month.atDay(1), month.atEndOfMonth()).stream()
                .collect(Collectors.toMap(CategorySum::getCategoryId, row -> row.getTotal().longValue()));

        return budgets.stream()
                .map(budget -> {
                    // Spending is negative in the ledger; refunds in the same category bring it back down
                    long spent = Math.max(0, -netByCategory.getOrDefault(budget.getCategory().getId(), 0L));
                    return toResponse(budget, spent);
                })
                .toList();
    }

    @Transactional
    public BudgetResponse create(long userId, BudgetRequest request) {
        Category category = categoryService.getVisible(userId, request.categoryId());
        if (category.getKind() != CategoryKind.EXPENSE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Budgets can only be set for spending categories");
        }
        if (budgetRepository.existsByUserIdAndCategoryId(userId, category.getId())) {
            throw alreadyBudgeted(category);
        }

        Budget budget = new Budget();
        budget.setUser(userRepository.getReferenceById(userId));
        budget.setCategory(category);
        budget.setLimitPaise(checkLimit(request.limitPaise()));

        try {
            budgetRepository.saveAndFlush(budget);
        } catch (DataIntegrityViolationException e) {
            throw alreadyBudgeted(category);
        }
        return progressOf(userId, budget);
    }

    @Transactional
    public BudgetResponse updateLimit(long userId, long budgetId, BudgetLimitRequest request) {
        Budget budget = getOwned(userId, budgetId);
        budget.setLimitPaise(checkLimit(request.limitPaise()));
        budgetRepository.saveAndFlush(budget);
        return progressOf(userId, budget);
    }

    @Transactional
    public void delete(long userId, long budgetId) {
        budgetRepository.delete(getOwned(userId, budgetId));
    }

    /** Average monthly spending per category over the last 3 complete months. */
    @Transactional(readOnly = true)
    public List<BudgetSuggestion> suggestions(long userId) {
        YearMonth lastFullMonth = AppTime.thisMonth(clock).minusMonths(1);
        YearMonth first = lastFullMonth.minusMonths(SUGGESTION_MONTHS - 1);

        return transactionRepository
                .spendingByCategory(userId, first.atDay(1), lastFullMonth.atEndOfMonth(), CategoryKind.TRANSFER)
                .stream()
                .filter(row -> row.getCategoryId() != null)
                .map(row -> new BudgetSuggestion(row.getCategoryId(), row.getName(), row.getColor(),
                        row.getTotal().longValue() / SUGGESTION_MONTHS))
                .toList();
    }

    private BudgetResponse progressOf(long userId, Budget budget) {
        return progress(userId, AppTime.thisMonth(clock)).stream()
                .filter(b -> b.id().equals(budget.getId()))
                .findFirst()
                .orElseThrow();
    }

    private Budget getOwned(long userId, long budgetId) {
        return budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Budget not found"));
    }

    private static long checkLimit(long limitPaise) {
        if (limitPaise > MAX_LIMIT_PAISE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That limit is too large");
        }
        return limitPaise;
    }

    private static ApiException alreadyBudgeted(Category category) {
        return new ApiException(HttpStatus.CONFLICT, "You already have a budget for " + category.getName());
    }

    private static BudgetResponse toResponse(Budget budget, long spent) {
        long limit = budget.getLimitPaise();
        int percent = (int) Math.min(999, Math.round(spent * 100.0 / limit));
        return new BudgetResponse(
                budget.getId(),
                budget.getCategory().getId(),
                budget.getCategory().getName(),
                budget.getCategory().getColor(),
                limit,
                spent,
                limit - spent,
                percent,
                BudgetStatus.of(spent, limit));
    }
}
