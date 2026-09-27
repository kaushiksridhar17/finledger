package com.kaushiksridhar.finledger.investment;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A purchase (BUY) or redemption (SELL). units is optional: leave it out and it's worked out from the
 * amount and that day's NAV; fill it in from your statement to match it to the last decimal.
 */
public record FundTransactionRequest(

        @NotNull(message = "Choose a fund")
        Integer schemeCode,

        @NotNull(message = "Choose buy or sell")
        MfTxnType type,

        @NotNull(message = "Choose a date")
        LocalDate date,

        @NotNull(message = "Enter an amount")
        @Positive(message = "The amount must be more than zero")
        Long amountPaise,

        @Positive(message = "Units must be more than zero")
        @DecimalMax(value = "1000000000", message = "That's too many units")
        BigDecimal units) {
}
