package com.kaushiksridhar.finledger.recurring;

import java.time.LocalDate;

/**
 * How often a payment repeats. Each frequency accepts a range of gaps between payments
 * (a "monthly" bill can land 26 to 35 days after the last one, since months differ in length
 * and banks shift dates around weekends), and says how long a silence means it has stopped.
 */
public enum Frequency {
    WEEKLY(5, 9, 14, 52.0 / 12),
    MONTHLY(26, 35, 45, 1),
    QUARTERLY(80, 100, 125, 1.0 / 3),
    YEARLY(350, 380, 400, 1.0 / 12);

    private final int minGapDays;
    private final int maxGapDays;
    private final int staleAfterDays;
    private final double timesPerMonth;

    Frequency(int minGapDays, int maxGapDays, int staleAfterDays, double timesPerMonth) {
        this.minGapDays = minGapDays;
        this.maxGapDays = maxGapDays;
        this.staleAfterDays = staleAfterDays;
        this.timesPerMonth = timesPerMonth;
    }

    public boolean accepts(long gapDays) {
        return gapDays >= minGapDays && gapDays <= maxGapDays;
    }

    public int staleAfterDays() {
        return staleAfterDays;
    }

    /** Roughly what this costs per month, for "your subscriptions cost Rs X a month". */
    public long monthlyEquivalent(long amountPaise) {
        return Math.round(amountPaise * timesPerMonth);
    }

    public LocalDate next(LocalDate last) {
        return switch (this) {
            case WEEKLY -> last.plusWeeks(1);
            case MONTHLY -> last.plusMonths(1);
            case QUARTERLY -> last.plusMonths(3);
            case YEARLY -> last.plusYears(1);
        };
    }

    /** The frequency whose range contains this typical gap, or null if none does. */
    public static Frequency forGap(long gapDays) {
        for (Frequency frequency : values()) {
            if (frequency.accepts(gapDays)) {
                return frequency;
            }
        }
        return null;
    }
}
