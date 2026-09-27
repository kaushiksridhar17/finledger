package com.kaushiksridhar.finledger.budget;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BudgetRequest(

        @NotNull(message = "Choose a category")
        Long categoryId,

        @NotNull(message = "Enter a monthly limit")
        @Positive(message = "The limit must be more than zero")
        Long limitPaise) {
}
