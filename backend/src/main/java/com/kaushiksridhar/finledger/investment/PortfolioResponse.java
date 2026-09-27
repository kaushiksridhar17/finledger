package com.kaushiksridhar.finledger.investment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The Investments page. Money in paise; xirr is a yearly rate as a fraction (0.1234 = 12.34%),
 * or null when there isn't enough history for it to mean anything.
 */
public record PortfolioResponse(
        long investedPaise,
        long valuePaise,
        long gainPaise,
        Double xirr,
        LocalDate asOf,
        List<Fund> funds,
        List<HistoryPoint> history,
        List<Trade> transactions,
        List<Sip> sips) {

    /** One fund you hold. units to 3 decimal places; latestNav is the price of one unit on navDate. */
    public record Fund(
            int schemeCode,
            String name,
            String fundHouse,
            String category,
            BigDecimal units,
            BigDecimal latestNav,
            LocalDate navDate,
            long investedPaise,
            long valuePaise,
            long gainPaise,
            Double xirr,
            boolean sip) {
    }

    /** Portfolio value and money invested at the end of a month, for the chart. */
    public record HistoryPoint(LocalDate date, long investedPaise, long valuePaise) {
    }

    public record Trade(
            Long id,
            int schemeCode,
            String schemeName,
            MfTxnType type,
            LocalDate date,
            long amountPaise,
            BigDecimal units,
            BigDecimal nav,
            boolean fromBank) {
    }

    public record Sip(Long id, int schemeCode, String schemeName, String label, int purchases) {
    }
}
