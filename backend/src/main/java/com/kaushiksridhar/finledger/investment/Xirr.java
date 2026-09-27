package com.kaushiksridhar.finledger.investment;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * XIRR: the yearly return that explains a series of cash flows on different dates.
 * It's what Excel's XIRR() and fund apps like Groww and Kuvera show, and the right measure for SIPs,
 * where each instalment has been invested for a different length of time.
 *
 * It finds the rate r where the present value of every flow adds up to zero:
 *
 *   sum of  amount_i / (1 + r) ^ (days_i / 365)  =  0
 *
 * There's no formula for r, so it's found numerically: Newton's method first (fast), and if that
 * doesn't settle, bisection (slower but always works when an answer exists).
 *
 * Money put in is negative, money taken out (or today's value) is positive.
 * Plain Java with no Spring, so it's fast to unit test.
 */
public final class Xirr {

    private static final double TOLERANCE = 1e-9;
    private static final int MAX_ITERATIONS = 200;
    private static final double LOWEST_RATE = -0.9999;   // -99.99%: (1 + r) must stay above zero

    private Xirr() {
    }

    public record CashFlow(LocalDate date, double amount) {
    }

    /** The yearly rate as a fraction (0.12 = 12%), or null if the flows don't have one. */
    public static Double calculate(List<CashFlow> flows) {
        boolean hasOut = flows.stream().anyMatch(f -> f.amount() < 0);
        boolean hasIn = flows.stream().anyMatch(f -> f.amount() > 0);
        if (!hasOut || !hasIn) {
            return null;
        }

        LocalDate start = flows.stream().map(CashFlow::date).min(LocalDate::compareTo).orElseThrow();
        double[] years = new double[flows.size()];
        double[] amounts = new double[flows.size()];
        double scale = 0;
        for (int i = 0; i < flows.size(); i++) {
            years[i] = ChronoUnit.DAYS.between(start, flows.get(i).date()) / 365.0;
            amounts[i] = flows.get(i).amount();
            scale = Math.max(scale, Math.abs(amounts[i]));
        }
        if (allSameDay(years)) {
            return null;
        }

        Double newton = newton(years, amounts, scale);
        return newton != null ? newton : bisection(years, amounts, scale);
    }

    /** Present value of every flow at rate r. */
    static double presentValue(double[] years, double[] amounts, double r) {
        double total = 0;
        for (int i = 0; i < years.length; i++) {
            total += amounts[i] / Math.pow(1 + r, years[i]);
        }
        return total;
    }

    private static double derivative(double[] years, double[] amounts, double r) {
        double total = 0;
        for (int i = 0; i < years.length; i++) {
            total -= years[i] * amounts[i] / Math.pow(1 + r, years[i] + 1);
        }
        return total;
    }

    private static Double newton(double[] years, double[] amounts, double scale) {
        double r = 0.1;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double value = presentValue(years, amounts, r);
            if (Math.abs(value) < TOLERANCE * scale) {
                return r;
            }
            double slope = derivative(years, amounts, r);
            if (slope == 0 || !Double.isFinite(slope)) {
                return null;
            }
            double next = r - value / slope;
            if (!Double.isFinite(next) || next <= LOWEST_RATE) {
                return null;   // wandered off; let bisection handle it
            }
            if (Math.abs(next - r) < 1e-12) {
                return next;
            }
            r = next;
        }
        return null;
    }

    private static Double bisection(double[] years, double[] amounts, double scale) {
        double low = LOWEST_RATE;
        double high = 1.0;
        double fLow = presentValue(years, amounts, low);
        double fHigh = presentValue(years, amounts, high);
        // Widen the upper bound until the answer is inside the range (up to 1,000,000% a year)
        while (Math.signum(fLow) == Math.signum(fHigh) && high < 1e4) {
            high *= 2;
            fHigh = presentValue(years, amounts, high);
        }
        if (Math.signum(fLow) == Math.signum(fHigh)) {
            return null;
        }

        for (int i = 0; i < 500; i++) {
            double mid = (low + high) / 2;
            double fMid = presentValue(years, amounts, mid);
            if (Math.abs(fMid) < TOLERANCE * scale || (high - low) < 1e-12) {
                return mid;
            }
            if (Math.signum(fMid) == Math.signum(fLow)) {
                low = mid;
                fLow = fMid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2;
    }

    private static boolean allSameDay(double[] years) {
        for (double y : years) {
            if (y != 0) {
                return false;
            }
        }
        return true;
    }
}
