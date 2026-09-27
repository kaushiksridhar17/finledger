package com.kaushiksridhar.finledger.recurring;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Finds payments that repeat on a schedule: subscriptions, rent, bills, SIPs, salary.
 *
 * 1. Group transactions by who they're with: the merchant name if known, otherwise the description
 *    with reference numbers and bank jargon stripped ("UPI/RENT/R KUMAR/4281..." -> "RENT R KUMAR").
 *    Money in and money out are grouped separately.
 * 2. For each group, look at the gaps in days between consecutive payments. The typical (median)
 *    gap decides the frequency: about 7 days is weekly, about 30 monthly, and so on.
 * 3. Keep the group only if it's regular (at least 75% of gaps fit that frequency), the amounts
 *    are similar (within 50% of the typical amount, so an electricity bill that varies still counts),
 *    it has happened enough times, and it hasn't stopped (the last payment isn't too long ago).
 *
 * Pure logic: no database, no Spring, no clock. "today" is passed in, so it's easy to test.
 */
public final class RecurringDetector {

    public record TxnPoint(
            LocalDate date,
            long amountPaise,
            String description,
            String merchant,
            Long categoryId,
            String categoryName,
            Long accountId) {
    }

    public record Detected(
            String key,
            String name,
            Direction direction,
            Frequency frequency,
            long amountPaise,
            long minAmountPaise,
            long maxAmountPaise,
            int occurrences,
            LocalDate lastSeen,
            LocalDate nextDue,
            Long categoryId,
            Long accountId) {
    }

    /** Moving money between your own accounts isn't a bill, even when it's regular. */
    static final Set<String> IGNORED_CATEGORIES = Set.of("Transfer", "Credit Card Payment");

    static final double REGULARITY = 0.75;
    static final double AMOUNT_TOLERANCE = 0.50;
    static final int MAX_KEY_LENGTH = 110;

    private static final Set<String> NOISE_WORDS = Set.of(
            "UPI", "NACH", "NEFT", "IMPS", "RTGS", "POS", "ECS", "ACH", "BIL", "ONL", "INB", "MB",
            "DR", "CR", "TO", "BY", "TRANSFER", "PAYMENT", "ORDER", "AUTOPAY", "PAID", "VIA", "APP");

    private RecurringDetector() {
    }

    public static List<Detected> detect(List<TxnPoint> transactions, LocalDate today) {
        Map<String, List<TxnPoint>> groups = new LinkedHashMap<>();

        for (TxnPoint t : transactions) {
            if (t.amountPaise() == 0 || (t.categoryName() != null && IGNORED_CATEGORIES.contains(t.categoryName()))) {
                continue;
            }
            String key = keyOf(t);
            if (key == null) {
                continue;
            }
            String directionalKey = (t.amountPaise() < 0 ? "OUT|" : "IN|") + key;
            groups.computeIfAbsent(directionalKey, k -> new ArrayList<>()).add(t);
        }

        List<Detected> found = new ArrayList<>();
        for (Map.Entry<String, List<TxnPoint>> group : groups.entrySet()) {
            Detected detected = analyse(group.getKey().substring(group.getKey().indexOf('|') + 1), group.getValue(), today);
            if (detected != null) {
                found.add(detected);
            }
        }

        found.sort(Comparator.comparing(Detected::nextDue).thenComparing(Detected::name));
        return found;
    }

    private static Detected analyse(String key, List<TxnPoint> group, LocalDate today) {
        // One payment per day: two chai payments on the same day aren't a schedule
        List<TxnPoint> byDay = group.stream()
                .sorted(Comparator.comparing(TxnPoint::date))
                .collect(Collectors.toMap(TxnPoint::date, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                .values().stream().toList();

        if (byDay.size() < 2) {
            return null;
        }

        long[] gaps = new long[byDay.size() - 1];
        for (int i = 1; i < byDay.size(); i++) {
            gaps[i - 1] = ChronoUnit.DAYS.between(byDay.get(i - 1).date(), byDay.get(i).date());
        }

        Frequency frequency = Frequency.forGap(median(gaps));
        if (frequency == null) {
            return null;
        }

        int needed = frequency == Frequency.YEARLY ? 2 : 3;
        if (byDay.size() < needed) {
            return null;
        }

        long regularGaps = Arrays.stream(gaps).filter(frequency::accepts).count();
        if (regularGaps < Math.ceil(gaps.length * REGULARITY)) {
            return null;
        }

        long[] amounts = byDay.stream().mapToLong(t -> Math.abs(t.amountPaise())).toArray();
        long typical = median(amounts);
        for (long amount : amounts) {
            if (Math.abs(amount - typical) > typical * AMOUNT_TOLERANCE) {
                return null;
            }
        }

        LocalDate last = byDay.get(byDay.size() - 1).date();
        if (ChronoUnit.DAYS.between(last, today) > frequency.staleAfterDays()) {
            return null;       // it has stopped
        }

        return new Detected(
                key,
                displayName(byDay),
                byDay.get(0).amountPaise() < 0 ? Direction.OUT : Direction.IN,
                frequency,
                typical,
                Arrays.stream(amounts).min().orElse(typical),
                Arrays.stream(amounts).max().orElse(typical),
                byDay.size(),
                last,
                frequency.next(last),
                mostCommon(byDay, TxnPoint::categoryId),
                mostCommon(byDay, TxnPoint::accountId));
    }

    /** "M:NETFLIX" when the merchant is known, otherwise "D:" + the cleaned description. */
    static String keyOf(TxnPoint t) {
        if (t.merchant() != null && !t.merchant().isBlank()) {
            return truncate("M:" + t.merchant().trim().toUpperCase(Locale.ROOT));
        }
        String cleaned = cleanDescription(t.description());
        return cleaned.isEmpty() ? null : truncate("D:" + cleaned);
    }

    /** "UPI/RENT/R KUMAR/428112345673" -> "RENT R KUMAR"; "SALARY ACME TECH AUG2026" -> "SALARY ACME TECH" */
    static String cleanDescription(String description) {
        return Arrays.stream(description.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+"))
                .filter(word -> !word.isEmpty())
                .filter(word -> word.chars().noneMatch(Character::isDigit))
                .filter(word -> !NOISE_WORDS.contains(word))
                .collect(Collectors.joining(" "));
    }

    private static String displayName(List<TxnPoint> payments) {
        TxnPoint latest = payments.get(payments.size() - 1);
        if (latest.merchant() != null && !latest.merchant().isBlank()) {
            return latest.merchant().trim();
        }
        String cleaned = cleanDescription(latest.description());
        String titled = Arrays.stream(cleaned.toLowerCase(Locale.ROOT).split(" "))
                .map(word -> word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
        return titled.length() <= 100 ? titled : titled.substring(0, 100);
    }

    private static <T> T mostCommon(List<TxnPoint> payments, Function<TxnPoint, T> field) {
        Map<T, Integer> counts = new HashMap<>();
        for (TxnPoint p : payments) {
            T value = field.apply(p);
            if (value != null) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.<T, Integer>comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    }

    private static String truncate(String key) {
        return key.length() <= MAX_KEY_LENGTH ? key : key.substring(0, MAX_KEY_LENGTH);
    }
}
