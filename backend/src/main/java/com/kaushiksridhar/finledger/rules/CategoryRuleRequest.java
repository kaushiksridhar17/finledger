package com.kaushiksridhar.finledger.rules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRuleRequest(

        @NotBlank(message = "Enter the text to look for")
        @Size(max = 100, message = "The text must be at most 100 characters")
        String pattern,

        @NotNull(message = "Choose how to match")
        RuleMatchType matchType,

        @NotNull(message = "Choose a category")
        Long categoryId) {
}
