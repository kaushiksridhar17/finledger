package com.kaushiksridhar.finledger.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kaushiksridhar.finledger.investment.Xirr.CashFlow;

/** Plain unit tests, checked against the answers Excel's XIRR() gives for the same flows. */
class XirrTest {

    @Test
    @DisplayName("Rs 1,000 growing to Rs 1,100 in exactly a year is 10%")
    void oneYearTenPercent() {
        Double rate = Xirr.calculate(List.of(
                flow("2025-01-01", -1_000),
                flow("2026-01-01", 1_100)));
        assertThat(rate).isCloseTo(0.10, within(1e-9));
    }

    @Test
    @DisplayName("matches Excel's own XIRR example: 37.34%")
    void excelExample() {
        Double rate = Xirr.calculate(List.of(
                flow("2008-01-01", -10_000),
                flow("2008-03-01", 2_750),
                flow("2008-10-30", 4_250),
                flow("2009-02-15", 3_250),
                flow("2009-04-01", 2_750)));
        assertThat(rate).isCloseTo(0.373362535, within(1e-6));
    }

    @Test
    @DisplayName("a year of monthly SIPs that lost money gives a negative rate, and a steep loss still converges")
    void losses() {
        List<CashFlow> flows = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 1, 10);
        for (int month = 0; month < 12; month++) {
            flows.add(new CashFlow(start.plusMonths(month), -5_000));
        }
        flows.add(new CashFlow(LocalDate.of(2026, 1, 10), 55_000));   // Rs 60,000 in, worth Rs 55,000
        Double rate = Xirr.calculate(flows);
        assertThat(rate).isNegative();
        assertThat(Xirr.presentValue(years(flows), amounts(flows), rate)).isCloseTo(0.0, within(1e-3));

        Double crash = Xirr.calculate(List.of(flow("2025-01-01", -1_000), flow("2026-01-01", 10)));
        assertThat(crash).isCloseTo(-0.99, within(1e-6));
    }

    @Test
    @DisplayName("no rate exists without money both going in and coming out, or when everything is on one day")
    void noAnswer() {
        assertThat(Xirr.calculate(List.of(flow("2025-01-01", -1_000), flow("2026-01-01", -500)))).isNull();
        assertThat(Xirr.calculate(List.of(flow("2025-01-01", -1_000), flow("2025-01-01", 1_100)))).isNull();
        assertThat(Xirr.calculate(List.of())).isNull();
    }

    private static CashFlow flow(String date, double amount) {
        return new CashFlow(LocalDate.parse(date), amount);
    }

    private static double[] years(List<CashFlow> flows) {
        LocalDate start = flows.get(0).date();
        return flows.stream().mapToDouble(f -> java.time.temporal.ChronoUnit.DAYS.between(start, f.date()) / 365.0).toArray();
    }

    private static double[] amounts(List<CashFlow> flows) {
        return flows.stream().mapToDouble(CashFlow::amount).toArray();
    }
}
