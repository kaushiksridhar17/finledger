package com.kaushiksridhar.finledger.transaction;

import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.account.AccountService;
import com.kaushiksridhar.finledger.category.CategoryService;
import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.common.PageResponse;
import com.kaushiksridhar.finledger.user.UserRepository;

@Service
public class TransactionService {

    static final int MAX_PAGE_SIZE = 100;

    // Rs 100 crore in paise. Anything bigger is almost certainly a typo.
    static final long MAX_ABS_AMOUNT_PAISE = 100_000_000_000L;

    // Newest first; id breaks ties between transactions on the same day
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "txnDate").and(Sort.by(Sort.Direction.DESC, "id"));

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    public TransactionService(TransactionRepository transactionRepository,
            AccountService accountService,
            CategoryService categoryService,
            UserRepository userRepository,
            ApplicationEventPublisher events) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.userRepository = userRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(long userId, TransactionFilter filter, int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Page must be 0 or more");
        }
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);

        String direction = normaliseDirection(filter.direction());
        String search = (filter.search() == null || filter.search().isBlank())
                ? null
                : "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%";

        var results = transactionRepository.search(
                userId,
                filter.accountId(),
                filter.categoryId(),
                filter.from(),
                filter.to(),
                direction,
                search,
                PageRequest.of(page, pageSize, NEWEST_FIRST));

        return PageResponse.from(results.map(TransactionResponse::from));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(long userId, long transactionId) {
        return TransactionResponse.from(getOwned(userId, transactionId));
    }

    @Transactional
    public TransactionResponse create(long userId, TransactionRequest request) {
        Transaction transaction = new Transaction();
        transaction.setUser(userRepository.getReferenceById(userId));
        apply(userId, transaction, request);
        TransactionResponse saved = TransactionResponse.from(transactionRepository.save(transaction));
        events.publishEvent(new LedgerChangedEvent(userId, false));   // budget alerts run after commit
        return saved;
    }

    @Transactional
    public TransactionResponse update(long userId, long transactionId, TransactionRequest request) {
        Transaction transaction = getOwned(userId, transactionId);
        apply(userId, transaction, request);
        TransactionResponse saved = TransactionResponse.from(transactionRepository.save(transaction));
        events.publishEvent(new LedgerChangedEvent(userId, false));
        return saved;
    }

    @Transactional
    public void delete(long userId, long transactionId) {
        transactionRepository.delete(getOwned(userId, transactionId));
        events.publishEvent(new LedgerChangedEvent(userId, false));
    }

    // Copies the request onto the entity after checking the account and category belong to this user
    private void apply(long userId, Transaction transaction, TransactionRequest request) {
        long amount = request.amountPaise();
        if (amount == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount cannot be zero");
        }
        if (Math.abs(amount) > MAX_ABS_AMOUNT_PAISE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount is too large");
        }

        transaction.setAccount(accountService.getOwned(userId, request.accountId()));
        transaction.setCategory(request.categoryId() == null
                ? null
                : categoryService.getVisible(userId, request.categoryId()));
        transaction.setAmountPaise(amount);
        transaction.setTxnDate(request.date());
        transaction.setDescription(request.description().trim());
        transaction.setMerchant(blankToNull(request.merchant()));
        transaction.setNotes(blankToNull(request.notes()));
    }

    private Transaction getOwned(long userId, long transactionId) {
        return transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    private static String normaliseDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return null;
        }
        String upper = direction.trim().toUpperCase(Locale.ROOT);
        if (!upper.equals("IN") && !upper.equals("OUT")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "direction must be IN or OUT");
        }
        return upper;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
