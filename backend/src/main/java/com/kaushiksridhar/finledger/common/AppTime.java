package com.kaushiksridhar.finledger.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * The app is built for India, so "today" and "this month" are worked out in Indian time.
 * Timestamps are still stored in UTC; this only decides which calendar day it is.
 */
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private AppTime() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }

    public static YearMonth thisMonth(Clock clock) {
        return YearMonth.from(today(clock));
    }
}
