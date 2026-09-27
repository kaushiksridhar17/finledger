package com.kaushiksridhar.finledger.split;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

import com.kaushiksridhar.finledger.common.MoneyText;

/**
 * Divides an expense into whole-paise shares that always add up to the total exactly.
 *
 * Dividing money rarely comes out even (Rs 100 between 3 is 33.333...), so every split works out
 * each person's exact fraction, rounds everyone down, then hands the few leftover paise to the
 * people whose fractions were rounded down the most (the "largest remainder" method). Ties go to
 * whoever comes first in the list, so the same input always gives the same result.
 *
 * Plain Java with no Spring, so it's fast to unit test.
 */
public final class SplitCalculator {

    /** 100% in basis points: 33.33% is sent as 3333. */
    public static final long PERCENT_SCALE = 10_000;

    public static final long MAX_SHARE_WEIGHT = 1_000;

    private SplitCalculator() {
    }

    /** One person in the split. value is what was typed for them; EQUAL splits ignore it. */
    public record Part(long memberId, Long value) {
    }

    /** Thrown for a split that can't work, with a message that can be shown to the user as it is. */
    public static class SplitException extends RuntimeException {
        public SplitException(String message) {
            super(message);
        }
    }

    /** Returns each person's share in paise, in the same order as parts. */
    public static List<Long> split(long totalPaise, SplitType type, List<Part> parts) {
        if (totalPaise <= 0) {
            throw new SplitException("The amount must be more than zero");
        }
        if (parts.isEmpty()) {
            throw new SplitException("Choose who this expense is split between");
        }
        Set<Long> seen = new HashSet<>();
        for (Part part : parts) {
            if (!seen.add(part.memberId())) {
                throw new SplitException("Each person can only appear once in a split");
            }
        }

        long[] shares = switch (type) {
            case EQUAL -> largestRemainder(totalPaise, IntStream.range(0, parts.size()).mapToLong(i -> 1).toArray());
            case EXACT -> exact(totalPaise, parts);
            case PERCENT -> percent(totalPaise, parts);
            case SHARES -> weighted(totalPaise, parts);
        };

        List<Long> result = new ArrayList<>(shares.length);
        for (long share : shares) {
            result.add(share);
        }
        return result;
    }

    private static long[] exact(long totalPaise, List<Part> parts) {
        long[] shares = new long[parts.size()];
        long sum = 0;
        for (int i = 0; i < parts.size(); i++) {
            long value = valueOf(parts.get(i), "Enter an amount for everyone in the split");
            if (value < 0) {
                throw new SplitException("Amounts can't be negative");
            }
            shares[i] = value;
            sum = Math.addExact(sum, value);
        }
        if (sum != totalPaise) {
            throw new SplitException("The amounts add up to " + MoneyText.format(sum)
                    + ", but the expense is " + MoneyText.format(totalPaise));
        }
        return shares;
    }

    private static long[] percent(long totalPaise, List<Part> parts) {
        long[] weights = new long[parts.size()];
        long sum = 0;
        for (int i = 0; i < parts.size(); i++) {
            long value = valueOf(parts.get(i), "Enter a percentage for everyone in the split");
            if (value < 0 || value > PERCENT_SCALE) {
                throw new SplitException("Each percentage must be between 0 and 100");
            }
            weights[i] = value;
            sum += value;
        }
        if (sum != PERCENT_SCALE) {
            throw new SplitException("The percentages add up to " + percentText(sum) + "% instead of 100%");
        }
        return largestRemainder(totalPaise, weights);
    }

    private static long[] weighted(long totalPaise, List<Part> parts) {
        long[] weights = new long[parts.size()];
        long sum = 0;
        for (int i = 0; i < parts.size(); i++) {
            long value = valueOf(parts.get(i), "Enter a number of shares for everyone in the split");
            if (value < 0 || value > MAX_SHARE_WEIGHT) {
                throw new SplitException("Shares must be whole numbers from 0 to " + MAX_SHARE_WEIGHT);
            }
            weights[i] = value;
            sum += value;
        }
        if (sum == 0) {
            throw new SplitException("Give at least one person a share");
        }
        return largestRemainder(totalPaise, weights);
    }

    /**
     * Splits total in proportion to weights. Everyone first gets their rounded-down share; the paise
     * left over (always fewer than the number of people) go one each to the largest remainders.
     */
    static long[] largestRemainder(long total, long[] weights) {
        long weightSum = 0;
        for (long weight : weights) {
            weightSum += weight;
        }

        int n = weights.length;
        long[] shares = new long[n];
        long[] remainders = new long[n];
        long assigned = 0;
        for (int i = 0; i < n; i++) {
            long exact = Math.multiplyExact(total, weights[i]);
            shares[i] = exact / weightSum;
            remainders[i] = exact % weightSum;
            assigned += shares[i];
        }

        long leftover = total - assigned;
        List<Integer> order = IntStream.range(0, n).boxed()
                .sorted(Comparator.<Integer>comparingLong(i -> remainders[i]).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        for (int k = 0; k < leftover; k++) {
            shares[order.get(k)]++;
        }
        return shares;
    }

    private static long valueOf(Part part, String missingMessage) {
        if (part.value() == null) {
            throw new SplitException(missingMessage);
        }
        return part.value();
    }

    /** 9000 -> "90", 3333 -> "33.33", 3350 -> "33.5" */
    static String percentText(long basisPoints) {
        long whole = basisPoints / 100;
        long rest = basisPoints % 100;
        if (rest == 0) {
            return Long.toString(whole);
        }
        String decimals = rest % 10 == 0 ? Long.toString(rest / 10) : String.format(Locale.ROOT, "%02d", rest);
        return whole + "." + decimals;
    }
}
