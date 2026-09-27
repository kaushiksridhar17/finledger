package com.kaushiksridhar.finledger.demo;

import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.account.Account;
import com.kaushiksridhar.finledger.account.AccountRepository;
import com.kaushiksridhar.finledger.budget.BudgetAlertService;
import com.kaushiksridhar.finledger.budget.BudgetRequest;
import com.kaushiksridhar.finledger.budget.BudgetService;
import com.kaushiksridhar.finledger.category.Category;
import com.kaushiksridhar.finledger.category.CategoryRepository;
import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.common.AppTime;
import com.kaushiksridhar.finledger.demo.DemoDataGenerator.AccountKey;
import com.kaushiksridhar.finledger.demo.DemoDataGenerator.DemoAccount;
import com.kaushiksridhar.finledger.demo.DemoDataGenerator.DemoTransaction;
import com.kaushiksridhar.finledger.recurring.Direction;
import com.kaushiksridhar.finledger.recurring.RecurringService;
import com.kaushiksridhar.finledger.recurring.RecurringStatus;
import com.kaushiksridhar.finledger.user.User;
import com.kaushiksridhar.finledger.user.UserRepository;

/**
 * "Try the demo": every click gets its own throwaway user filled with a year of history, budgets,
 * bills and split groups, so visitors never see or change each other's data.
 * DemoCleanupJob deletes them after the TTL.
 */
@Service
public class DemoService {

    static final String DEMO_NAME = "Arjun Mehta";
    private static final long SEED = 42L;

    // Monthly limits set up for the demo user, so the Budgets page has something to show
    private static final Map<String, Long> DEMO_BUDGETS = Map.of(
            "Food & Dining", 1_000_000L,
            "Groceries", 800_000L,
            "Shopping", 500_000L,
            "Entertainment", 150_000L,
            "Transport", 300_000L);

    private static final String INSERT_TRANSACTION = """
            INSERT INTO transactions
                (user_id, account_id, category_id, amount_paise, txn_date, description, merchant, notes, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final DemoProperties properties;
    private final BudgetService budgetService;
    private final BudgetAlertService budgetAlertService;
    private final RecurringService recurringService;
    private final DemoSplitGroups demoSplitGroups;
    private final Clock clock;

    public DemoService(UserRepository userRepository,
            AccountRepository accountRepository,
            CategoryRepository categoryRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate,
            DemoProperties properties,
            BudgetService budgetService,
            BudgetAlertService budgetAlertService,
            RecurringService recurringService,
            DemoSplitGroups demoSplitGroups,
            Clock clock) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.budgetService = budgetService;
        this.budgetAlertService = budgetAlertService;
        this.recurringService = recurringService;
        this.demoSplitGroups = demoSplitGroups;
        this.clock = clock;
    }

    @Transactional
    public User createDemoUser() {
        if (!properties.enabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "The demo is switched off");
        }

        Instant now = clock.instant();
        if (userRepository.countByDemoExpiresAtAfter(now) >= properties.maxActive()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Too many people are trying the demo right now. Please try again in a while.");
        }

        // A random password nobody knows: demo users can only get in through the demo button
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User(DEMO_NAME, "demo-" + suffix + "@demo.finledger.local",
                passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setDemoExpiresAt(now.plus(properties.ttl()));
        userRepository.save(user);

        Map<AccountKey, Long> accountIds = createAccounts(user);
        Map<String, Long> categoryIds = categoryRepository.findVisibleTo(user.getId()).stream()
                .collect(Collectors.toMap(Category::getName, Category::getId));

        LocalDate today = LocalDate.ofInstant(now, AppTime.ZONE);
        List<DemoTransaction> transactions = DemoDataGenerator.generate(today, SEED);
        insertTransactions(user.getId(), transactions, accountIds, categoryIds, now);
        setUpBudgetsAndBills(user.getId(), categoryIds);
        demoSplitGroups.create(user.getId(), accountIds.get(AccountKey.HDFC), categoryIds.get("Transfer"), today);

        return user;
    }

    /**
     * Gives the demo user some budgets, detects their repeating payments, and confirms the bills
     * and subscriptions so reminders appear. Salary and interest are left as suggestions, so a
     * visitor can try confirming one. All of this runs in the same transaction as the data above.
     */
    private void setUpBudgetsAndBills(long userId, Map<String, Long> categoryIds) {
        DEMO_BUDGETS.forEach((category, limit) ->
                budgetService.create(userId, new BudgetRequest(categoryIds.get(category), limit)));

        recurringService.scan(userId);
        recurringService.overview(userId).items().stream()
                .filter(item -> item.direction() == Direction.OUT)
                .forEach(item -> recurringService.setStatus(userId, item.id(), RecurringStatus.CONFIRMED));

        budgetAlertService.checkCurrentMonth(userId);
    }

    private Map<AccountKey, Long> createAccounts(User user) {
        Map<AccountKey, Long> ids = new EnumMap<>(AccountKey.class);
        for (DemoAccount spec : DemoDataGenerator.ACCOUNTS) {
            Account account = new Account();
            account.setUser(user);
            account.setName(spec.name());
            account.setType(spec.type());
            account.setOpeningBalancePaise(spec.openingBalancePaise());
            ids.put(spec.key(), accountRepository.save(account).getId());
        }
        return ids;
    }

    /**
     * About 750 rows, so they go in as JDBC batches rather than one JPA save each.
     * Runs in the same database transaction as the user and accounts above.
     */
    private void insertTransactions(long userId, List<DemoTransaction> transactions,
            Map<AccountKey, Long> accountIds, Map<String, Long> categoryIds, Instant now) {

        LocalDateTime createdAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);

        jdbcTemplate.batchUpdate(INSERT_TRANSACTION, transactions, 500, (ps, t) -> {
            ps.setLong(1, userId);
            ps.setLong(2, accountIds.get(t.account()));

            Long categoryId = categoryIds.get(t.category());
            if (categoryId == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, categoryId);
            }

            ps.setLong(4, t.amountPaise());
            ps.setObject(5, t.date());
            ps.setString(6, t.description());
            ps.setString(7, t.merchant());
            ps.setNull(8, Types.VARCHAR);
            ps.setObject(9, createdAt);
            ps.setObject(10, createdAt);
        });
    }
}
