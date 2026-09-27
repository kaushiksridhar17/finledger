package com.kaushiksridhar.finledger.support;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.kaushiksridhar.finledger.common.AppTime;
import com.kaushiksridhar.finledger.investment.NavPoint;
import com.kaushiksridhar.finledger.investment.NavSource;
import com.kaushiksridhar.finledger.investment.NavUnavailableException;
import com.kaushiksridhar.finledger.investment.SchemeData;
import com.kaushiksridhar.finledger.investment.SchemeHit;

/**
 * Fund data for tests, so they never touch the internet (application-test.properties picks it).
 * NAVs exist on weekdays only, from 800 days ago up to yesterday (today's isn't "published" yet).
 *
 *  - STEADY_GROWTH: NAV 100 until 180 days ago, 120 after that, so expected values are easy to work out
 *  - UNREACHABLE: always fails, like mfapi.in being down
 *  - any other code: a gently rising NAV
 */
@Component
@ConditionalOnProperty(name = "finledger.mf.source", havingValue = "fake")
public class FakeNavSource implements NavSource {

    public static final int STEADY_GROWTH = 100001;
    public static final int UNREACHABLE = 999999;

    private static final Map<Integer, String> NAMES = Map.of(
            STEADY_GROWTH, "Steady Growth Fund - Direct Plan - Growth",
            122639, "Parag Parikh Flexi Cap Fund - Direct Plan - Growth",
            120716, "UTI Nifty 50 Index Fund - Direct Plan - Growth");

    @Override
    public List<SchemeHit> search(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        return NAMES.entrySet().stream()
                .filter(e -> e.getValue().toLowerCase(Locale.ROOT).contains(lower))
                .map(e -> new SchemeHit(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public SchemeData fetch(int schemeCode) {
        if (schemeCode == UNREACHABLE) {
            throw new NavUnavailableException("Pretending mfapi.in is down", false, null);
        }
        LocalDate today = LocalDate.now(AppTime.ZONE);
        List<NavPoint> navs = new ArrayList<>();
        for (LocalDate day = today.minusDays(800); day.isBefore(today); day = day.plusDays(1)) {
            if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            navs.add(new NavPoint(day, navOn(schemeCode, day, today)));
        }
        String name = NAMES.getOrDefault(schemeCode, "Test Fund " + schemeCode + " - Direct Plan - Growth");
        return new SchemeData(schemeCode, name, "Test Mutual Fund", "Equity Scheme - Flexi Cap Fund", navs);
    }

    public static BigDecimal navOn(int schemeCode, LocalDate day, LocalDate today) {
        if (schemeCode == STEADY_GROWTH) {
            return day.isBefore(today.minusDays(180)) ? new BigDecimal("100.00000") : new BigDecimal("120.00000");
        }
        // 50.00 on the first day, rising by 0.01 a day
        long daysFromStart = ChronoUnit.DAYS.between(today.minusDays(800), day);
        return new BigDecimal("50.00000").add(BigDecimal.valueOf(daysFromStart, 2));
    }
}
