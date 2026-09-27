package com.kaushiksridhar.finledger.investment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.kaushiksridhar.finledger.investment.Xirr.CashFlow;

/**
 * Units, cost and value for one fund, worked out from its purchases and redemptions.
 *
 * Money stays in whole paise; units (3 decimal places, as fund houses allot them) and NAVs
 * (up to 5 decimal places) are BigDecimal, so nothing is ever a floating-point amount.
 *
 * Cost uses the average cost method: selling 40% of your units removes 40% of what they cost,
 * so "invested" is always what the units you still hold cost you.
 *
 * Plain Java with no Spring, so it's fast to unit test.
 */
public final class PortfolioMath {

    public static final int UNIT_SCALE = 3;

    private PortfolioMath() {
    }

    public record Trade(LocalDate date, MfTxnType type, long amountPaise, BigDecimal units) {
    }

    /** What you hold and what it cost you. */
    public record Position(BigDecimal units, long investedPaise) {

        public boolean isEmpty() {
            return units.signum() == 0;
        }
    }

    /** Units bought with an amount at a NAV, to 3 decimal places. */
    public static BigDecimal unitsFor(long amountPaise, BigDecimal nav) {
        return BigDecimal.valueOf(amountPaise)
                .divide(BigDecimal.valueOf(100))
                .divide(nav, UNIT_SCALE, RoundingMode.HALF_UP);
    }

    /** Units times NAV, in paise. */
    public static long valuePaise(BigDecimal units, BigDecimal nav) {
        return units.multiply(nav).multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** The position after every trade up to and including asOf. */
    public static Position positionAsOf(List<Trade> trades, LocalDate asOf) {
        BigDecimal units = BigDecimal.ZERO.setScale(UNIT_SCALE);
        long invested = 0;
        for (Trade trade : sorted(trades)) {
            if (trade.date().isAfter(asOf)) {
                break;
            }
            if (trade.type() == MfTxnType.BUY) {
                units = units.add(trade.units());
                invested += trade.amountPaise();
            } else if (units.signum() > 0) {
                BigDecimal sold = trade.units().min(units);
                long costOfSold = BigDecimal.valueOf(invested)
                        .multiply(sold)
                        .divide(units, 0, RoundingMode.HALF_UP)
                        .longValueExact();
                units = units.subtract(sold);
                invested -= costOfSold;
            }
        }
        return new Position(units, units.signum() == 0 ? 0 : invested);
    }

    /**
     * True if no redemption ever sells more units than were held at the time. A sale dated before the
     * purchase it relies on, or deleting a purchase a later sale needed, would break this.
     */
    public static boolean neverOversold(List<Trade> trades) {
        BigDecimal units = BigDecimal.ZERO;
        for (Trade trade : sorted(trades)) {
            units = trade.type() == MfTxnType.BUY ? units.add(trade.units()) : units.subtract(trade.units());
            if (units.signum() < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * The cash flows for XIRR: purchases are money out, redemptions and today's value are money in.
     */
    public static List<CashFlow> cashFlows(List<Trade> trades, long currentValuePaise, LocalDate asOf) {
        List<CashFlow> flows = new ArrayList<>();
        for (Trade trade : sorted(trades)) {
            double rupees = trade.amountPaise() / 100.0;
            flows.add(new CashFlow(trade.date(), trade.type() == MfTxnType.BUY ? -rupees : rupees));
        }
        if (currentValuePaise > 0) {
            flows.add(new CashFlow(asOf, currentValuePaise / 100.0));
        }
        return flows;
    }

    private static List<Trade> sorted(List<Trade> trades) {
        // Purchases before redemptions on the same day, so a buy-and-sell on one date works
        return trades.stream()
                .sorted(Comparator.comparing(Trade::date).thenComparing(Trade::type))
                .toList();
    }
}
