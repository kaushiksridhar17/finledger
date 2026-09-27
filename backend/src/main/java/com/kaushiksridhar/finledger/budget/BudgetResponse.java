package com.kaushiksridhar.finledger.budget;

/** A budget with how much of it is used in the month being viewed. percent can go past 100. */
public record BudgetResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String categoryColor,
        long limitPaise,
        long spentPaise,
        long remainingPaise,
        int percent,
        BudgetStatus status) {
}
