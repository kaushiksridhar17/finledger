package com.kaushiksridhar.finledger.account;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.transaction.TransactionRepository;
import com.kaushiksridhar.finledger.user.UserRepository;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Active accounts first, then archived, each with its live balance. */
    @Transactional(readOnly = true)
    public List<AccountResponse> list(long userId) {
        Map<Long, Long> totals = transactionRepository.totalsByAccount(userId);

        return accountRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(account -> AccountResponse.from(account, totals.getOrDefault(account.getId(), 0L)))
                .sorted((a, b) -> Boolean.compare(a.archived(), b.archived()))
                .toList();
    }

    @Transactional
    public AccountResponse create(long userId, AccountRequest request) {
        String name = request.name().trim();
        if (accountRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw nameTaken();
        }

        Account account = new Account();
        account.setUser(userRepository.getReferenceById(userId));
        account.setName(name);
        account.setType(request.type());
        account.setOpeningBalancePaise(request.openingBalancePaise());

        return AccountResponse.from(save(account), 0L);
    }

    @Transactional
    public AccountResponse update(long userId, long accountId, AccountRequest request) {
        Account account = getOwned(userId, accountId);

        String name = request.name().trim();
        boolean renamed = !name.equalsIgnoreCase(account.getName());
        if (renamed && accountRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw nameTaken();
        }

        account.setName(name);
        account.setType(request.type());
        account.setOpeningBalancePaise(request.openingBalancePaise());

        if (Boolean.TRUE.equals(request.archived()) && account.getArchivedAt() == null) {
            account.setArchivedAt(clock.instant());
        } else if (Boolean.FALSE.equals(request.archived())) {
            account.setArchivedAt(null);
        }

        Account saved = save(account);
        long total = transactionRepository.totalsByAccount(userId).getOrDefault(saved.getId(), 0L);
        return AccountResponse.from(saved, total);
    }

    /** Only empty accounts can be deleted. Accounts with history are archived instead, so no data is lost. */
    @Transactional
    public void delete(long userId, long accountId) {
        Account account = getOwned(userId, accountId);

        if (transactionRepository.existsByAccountId(account.getId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This account has transactions, so it can't be deleted. Archive it instead.");
        }

        accountRepository.delete(account);
    }

    /** Used by other services too. Returns 404 for accounts that don't exist or belong to someone else. */
    public Account getOwned(long userId, long accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private Account save(Account account) {
        try {
            return accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // Unique (user_id, name) constraint caught a duplicate that slipped past the check above
            throw nameTaken();
        }
    }

    private static ApiException nameTaken() {
        return new ApiException(HttpStatus.CONFLICT, "You already have an account with this name");
    }
}
