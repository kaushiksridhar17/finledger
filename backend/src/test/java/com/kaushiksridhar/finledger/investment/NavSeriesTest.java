package com.kaushiksridhar.finledger.investment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Plain unit tests of NAV lookups around weekends and missing days. */
class NavSeriesTest {

    // Friday 18 and Monday 21 September 2026 only
    private final NavSeries series = new NavSeries(List.of(
            new NavPoint(LocalDate.of(2026, 9, 18), new BigDecimal("100.00000")),
            new NavPoint(LocalDate.of(2026, 9, 21), new BigDecimal("101.50000"))));

    @Test
    @DisplayName("a holding is valued at the latest NAV on or before the day")
    void valueOn() {
        assertThat(series.valueOn(LocalDate.of(2026, 9, 20)).orElseThrow().nav()).isEqualByComparingTo("100");
        assertThat(series.valueOn(LocalDate.of(2026, 9, 25)).orElseThrow().nav()).isEqualByComparingTo("101.5");
        assertThat(series.valueOn(LocalDate.of(2026, 9, 1))).isEmpty();
    }

    @Test
    @DisplayName("money paid on a Saturday is invested at Monday's NAV")
    void weekendPurchase() {
        NavPoint point = series.tradeOn(LocalDate.of(2026, 9, 19)).orElseThrow();
        assertThat(point.date()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(point.nav()).isEqualByComparingTo("101.5");
        assertThat(series.publishedTradeOn(LocalDate.of(2026, 9, 19))).isPresent();
    }

    @Test
    @DisplayName("a purchase after the last NAV borrows it for manual entries, but SIPs wait for the real one")
    void afterLatest() {
        LocalDate tuesday = LocalDate.of(2026, 9, 22);
        assertThat(series.tradeOn(tuesday).orElseThrow().nav()).isEqualByComparingTo("101.5");
        assertThat(series.publishedTradeOn(tuesday)).isEmpty();
        assertThat(series.tradeOn(LocalDate.of(2026, 8, 1))).isEmpty();
    }
}
