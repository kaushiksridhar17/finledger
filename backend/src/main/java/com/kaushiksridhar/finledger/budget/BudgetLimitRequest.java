package com.kaushiksridhar.finledger.budget;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Editing a budget only changes its limit. To budget a different category, add a new budget. */
public record BudgetLimitRequest(

        @NotNull(message = "Enter a monthly limit")
        @Positive(message = "The limit must be more than zero")
        Long limitPaise) {
}
