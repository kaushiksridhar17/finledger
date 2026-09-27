package com.kaushiksridhar.finledger.investment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A fund's NAV history, with the two lookups the app needs.
 * NAVs are only published on business days, so there's often no NAV on the exact date asked for.
 */
public final class NavSeries {

    /** A purchase on a holiday gets the next business day's NAV, if there is one within this many days. */
    static final int NEXT_NAV_WINDOW_DAYS = 7;

    private final TreeMap<LocalDate, BigDecimal> navs = new TreeMap<>();

    public NavSeries(List<NavPoint> points) {
        for (NavPoint point : points) {
            navs.put(point.date(), point.nav());
        }
    }

    public boolean isEmpty() {
        return navs.isEmpty();
    }

    /** What a holding is worth on a date: the latest NAV on or before it. */
    public Optional<NavPoint> valueOn(LocalDate date) {
        Map.Entry<LocalDate, BigDecimal> entry = navs.floorEntry(date);
        return Optional.ofNullable(entry).map(e -> new NavPoint(e.getKey(), e.getValue()));
    }

    /**
     * The NAV a purchase or redemption on this date gets. Money that arrives on a weekend or holiday is
     * invested at the next business day's NAV, so that's used if it exists; otherwise (the NAV isn't
     * published yet) the latest one before.
     */
    public Optional<NavPoint> tradeOn(LocalDate date) {
        Map.Entry<LocalDate, BigDecimal> next = navs.ceilingEntry(date);
        if (next != null && !next.getKey().isAfter(date.plusDays(NEXT_NAV_WINDOW_DAYS))) {
            return Optional.of(new NavPoint(next.getKey(), next.getValue()));
        }
        return valueOn(date);
    }

    /**
     * Like tradeOn, but only a NAV on or after the date counts. Used for SIPs found in bank statements,
     * which wait for the real NAV instead of borrowing yesterday's.
     */
    public Optional<NavPoint> publishedTradeOn(LocalDate date) {
        Map.Entry<LocalDate, BigDecimal> next = navs.ceilingEntry(date);
        if (next != null && !next.getKey().isAfter(date.plusDays(NEXT_NAV_WINDOW_DAYS))) {
            return Optional.of(new NavPoint(next.getKey(), next.getValue()));
        }
        return Optional.empty();
    }

    public Optional<LocalDate> firstDate() {
        return navs.isEmpty() ? Optional.empty() : Optional.of(navs.firstKey());
    }
}
