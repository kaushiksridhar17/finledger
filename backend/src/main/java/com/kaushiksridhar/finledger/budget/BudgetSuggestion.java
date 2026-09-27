package com.kaushiksridhar.finledger.budget;

/** "You usually spend about Rs X a month on this", to help pick a sensible limit. */
public record BudgetSuggestion(Long categoryId, String name, String color, long averageMonthlyPaise) {
}
