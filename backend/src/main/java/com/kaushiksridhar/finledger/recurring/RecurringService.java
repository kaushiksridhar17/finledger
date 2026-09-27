package com.kaushiksridhar.finledger.recurring;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.account.AccountRepository;
import com.kaushiksridhar.finledger.category.CategoryRepository;
import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.common.AppTime;
import com.kaushiksridhar.finledger.common.MoneyText;
import com.kaushiksridhar.finledger.notification.NotificationService;
import com.kaushiksridhar.finledger.notification.NotificationType;
import com.kaushiksridhar.finledger.recurring.RecurringDetector.Detected;
import com.kaushiksridhar.finledger.recurring.RecurringDetector.TxnPoint;
import com.kaushiksridhar.finledger.transaction.Transaction;
import com.kaushiksridhar.finledger.user.UserRepository;

/**
 * Runs the detector over a user's last 13 months and keeps recurring_payments in step with it:
 * new series arrive as SUGGESTED, known ones get their latest amount and next due date, and the
 * user's own choice (CONFIRMED or DISMISSED) is never overwritten by a rescan.
 */
@Service
public class RecurringService {

    static final int HISTORY_MONTHS = 13;
    static final int REMIND_DAYS_AHEAD = 3;
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final RecurringPaymentRepository recurringRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public RecurringService(RecurringPaymentRepository recurringRepository,
            CategoryRepository categoryRepository,
            AccountRepository accountRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            Clock clock) {
        this.recurringRepository = recurringRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public ScanResult scan(long userId) {
        LocalDate today = AppTime.today(clock);

        List<TxnPoint> points = recurringRepository.transactionsSince(userId, today.minusMonths(HISTORY_MONTHS)).stream()
                .map(RecurringService::toPoint)
                .toList();
        List<Detected> detected = RecurringDetector.detect(points, today);

        Map<String, RecurringPayment> existing = recurringRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(RecurringPayment::getMatchKey, Function.identity()));

        List<RecurringPayment> added = new ArrayList<>();
        for (Detected d : detected) {
            RecurringPayment payment = existing.get(d.key());
            if (payment == null) {
                payment = new RecurringPayment();
                payment.setUser(userRepository.getReferenceById(userId));
                payment.setMatchKey(d.key());
                payment.setStatus(RecurringStatus.SUGGESTED);
                added.add(payment);
            }
            apply(payment, d);
            recurringRepository.save(payment);
        }

        if (!added.isEmpty()) {
            notifyFound(userId, today, added);
        }
        sendBillReminders(userId);

        return new ScanResult(detected.size(), added.size());
    }

    @Transactional(readOnly = true)
    public RecurringOverview overview(long userId) {
        LocalDate today = AppTime.today(clock);
        List<RecurringPaymentResponse> items = recurringRepository.findByUserIdOrderByNextDueOnAscNameAsc(userId).stream()
                .map(p -> RecurringPaymentResponse.from(p, today))
                .toList();

        long monthlyOut = items.stream()
                .filter(i -> i.status() == RecurringStatus.CONFIRMED && i.direction() == Direction.OUT)
                .mapToLong(RecurringPaymentResponse::monthlyEquivalentPaise)
                .sum();

        return new RecurringOverview(monthlyOut, items);
    }

    /** Confirmed bills and subscriptions due in the next few days, soonest first. */
    @Transactional(readOnly = true)
    public List<RecurringPaymentResponse> upcoming(long userId, int days) {
        LocalDate today = AppTime.today(clock);
        return recurringRepository
                .findByUserIdAndStatusAndDirectionAndNextDueOnBetweenOrderByNextDueOnAsc(
                        userId, RecurringStatus.CONFIRMED, Direction.OUT, today, today.plusDays(days))
                .stream()
                .map(p -> RecurringPaymentResponse.from(p, today))
                .toList();
    }

    @Transactional
    public RecurringPaymentResponse setStatus(long userId, long id, RecurringStatus status) {
        RecurringPayment payment = recurringRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Recurring payment not found"));
        payment.setStatus(status);
        recurringRepository.save(payment);

        if (status == RecurringStatus.CONFIRMED) {
            sendBillReminders(userId);
        }
        return RecurringPaymentResponse.from(payment, AppTime.today(clock));
    }

    /** "Netflix is due in 2 days". Once per payment per due date. */
    @Transactional
    public void sendBillReminders(long userId) {
        LocalDate today = AppTime.today(clock);
        List<RecurringPayment> dueSoon = recurringRepository
                .findByUserIdAndStatusAndDirectionAndNextDueOnBetweenOrderByNextDueOnAsc(
                        userId, RecurringStatus.CONFIRMED, Direction.OUT, today, today.plusDays(REMIND_DAYS_AHEAD));

        for (RecurringPayment p : dueSoon) {
            long days = ChronoUnit.DAYS.between(today, p.getNextDueOn());
            String when = days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days";
            String amount = p.getMinAmountPaise() == p.getMaxAmountPaise()
                    ? MoneyText.format(p.getAmountPaise())
                    : "about " + MoneyText.format(p.getAmountPaise());

            notificationService.notify(userId, NotificationType.BILL_DUE,
                    "bill:" + p.getId() + ":" + p.getNextDueOn(),
                    p.getName() + " is due " + when,
                    amount + " expected on " + p.getNextDueOn().format(DAY_MONTH) + ".",
                    "/recurring");
        }
    }

    private void apply(RecurringPayment payment, Detected d) {
        payment.setName(d.name());
        payment.setDirection(d.direction());
        payment.setFrequency(d.frequency());
        payment.setAmountPaise(d.amountPaise());
        payment.setMinAmountPaise(d.minAmountPaise());
        payment.setMaxAmountPaise(d.maxAmountPaise());
        payment.setOccurrences(d.occurrences());
        payment.setLastSeenOn(d.lastSeen());
        payment.setNextDueOn(d.nextDue());
        payment.setCategory(d.categoryId() == null ? null : categoryRepository.getReferenceById(d.categoryId()));
        payment.setAccount(d.accountId() == null ? null : accountRepository.getReferenceById(d.accountId()));
    }

    private void notifyFound(long userId, LocalDate today, List<RecurringPayment> added) {
        List<String> names = added.stream().map(RecurringPayment::getName).toList();
        String list = names.size() <= 3
                ? String.join(", ", names)
                : String.join(", ", names.subList(0, 3)) + " and " + (names.size() - 3) + " more";

        notificationService.notify(userId, NotificationType.RECURRING_FOUND,
                "recurring-found:" + today + ":" + String.join("|", names).hashCode(),
                names.size() == 1 ? "Found a repeating payment" : "Found " + names.size() + " repeating payments",
                list + ". Confirm the ones you want reminders for.",
                "/recurring");
    }

    private static TxnPoint toPoint(Transaction t) {
        return new TxnPoint(
                t.getTxnDate(),
                t.getAmountPaise(),
                t.getDescription(),
                t.getMerchant(),
                t.getCategory() != null ? t.getCategory().getId() : null,
                t.getCategory() != null ? t.getCategory().getName() : null,
                t.getAccount().getId());
    }
}
