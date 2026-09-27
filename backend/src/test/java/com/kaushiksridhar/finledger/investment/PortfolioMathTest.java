package com.kaushiksridhar.finledger.investment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kaushiksridhar.finledger.investment.PortfolioMath.Position;
import com.kaushiksridhar.finledger.investment.PortfolioMath.Trade;

/** Plain unit tests of units, cost and value. */
class PortfolioMathTest {

    @Test
    @DisplayName("units are allotted to 3 decimal places and valued to the paisa")
    void unitsAndValue() {
        // Rs 5,000 at a NAV of 89.958 is 55.581 units
        BigDecimal units = PortfolioMath.unitsFor(500_000, new BigDecimal("89.95800"));
        assertThat(units).isEqualByComparingTo("55.581");
        assertThat(units.scale()).isEqualTo(3);
        // 55.581 units at 92.12345 = Rs 5,120.31 (5,120.3135 rounded to the paisa)
        assertThat(PortfolioMath.valuePaise(units, new BigDecimal("92.12345"))).isEqualTo(512_031);
    }

    @Test
    @DisplayName("selling part of a holding removes the average cost of the units sold")
    void averageCost() {
        List<Trade> trades = List.of(
                new Trade(LocalDate.of(2026, 1, 10), MfTxnType.BUY, 1_000_000, new BigDecimal("100.000")),   // Rs 100 each
                new Trade(LocalDate.of(2026, 2, 10), MfTxnType.BUY, 1_000_000, new BigDecimal("50.000")),    // Rs 200 each
                new Trade(LocalDate.of(2026, 3, 10), MfTxnType.SELL, 900_000, new BigDecimal("60.000")));

        Position beforeSale = PortfolioMath.positionAsOf(trades, LocalDate.of(2026, 3, 1));
        assertThat(beforeSale.units()).isEqualByComparingTo("150");
        assertThat(beforeSale.investedPaise()).isEqualTo(2_000_000);

        // Average cost is Rs 133.33 a unit; selling 60 of 150 units removes 40% of the cost
        Position after = PortfolioMath.positionAsOf(trades, LocalDate.of(2026, 9, 1));
        assertThat(after.units()).isEqualByComparingTo("90");
        assertThat(after.investedPaise()).isEqualTo(1_200_000);

        Position everythingSold = PortfolioMath.positionAsOf(List.of(
                new Trade(LocalDate.of(2026, 1, 10), MfTxnType.BUY, 1_000_000, new BigDecimal("100.000")),
                new Trade(LocalDate.of(2026, 1, 20), MfTxnType.SELL, 1_100_000, new BigDecimal("100.000"))),
                LocalDate.of(2026, 9, 1));
        assertThat(everythingSold.isEmpty()).isTrue();
        assertThat(everythingSold.investedPaise()).isZero();
    }

    @Test
    @DisplayName("a redemption can't sell units that weren't held yet, or that a deleted purchase provided")
    void neverOversold() {
        Trade buy = new Trade(LocalDate.of(2026, 1, 10), MfTxnType.BUY, 1_000_000, new BigDecimal("100.000"));
        Trade laterSale = new Trade(LocalDate.of(2026, 3, 10), MfTxnType.SELL, 600_000, new BigDecimal("50.000"));
        Trade earlierSale = new Trade(LocalDate.of(2025, 12, 1), MfTxnType.SELL, 600_000, new BigDecimal("50.000"));

        assertThat(PortfolioMath.neverOversold(List.of(buy, laterSale))).isTrue();
        assertThat(PortfolioMath.neverOversold(List.of(buy, earlierSale))).isFalse();
        assertThat(PortfolioMath.neverOversold(List.of(laterSale))).isFalse();
        // Buying and selling on the same day counts the purchase first
        assertThat(PortfolioMath.neverOversold(List.of(
                new Trade(LocalDate.of(2026, 1, 10), MfTxnType.SELL, 600_000, new BigDecimal("50.000")), buy))).isTrue();
    }

    @Test
    @DisplayName("cash flows for XIRR: purchases out, redemptions and today's value in")
    void cashFlows() {
        List<Trade> trades = List.of(
                new Trade(LocalDate.of(2025, 9, 27), MfTxnType.BUY, 1_000_000, new BigDecimal("100.000")),
                new Trade(LocalDate.of(2026, 3, 27), MfTxnType.SELL, 600_000, new BigDecimal("50.000")));

        List<Xirr.CashFlow> flows = PortfolioMath.cashFlows(trades, 650_000, LocalDate.of(2026, 9, 27));
        assertThat(flows).extracting(Xirr.CashFlow::amount).containsExactly(-10_000.0, 6_000.0, 6_500.0);
        assertThat(Xirr.calculate(flows)).isPositive();
    }
}
