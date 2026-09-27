package com.kaushiksridhar.finledger.split;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A shared expense. shares lists everyone in the split; each value means something different per split type:
 * ignored for EQUAL, paise for EXACT, basis points for PERCENT (33.33% = 3333), a whole number for SHARES.
 */
public record ExpenseRequest(

        @NotBlank(message = "Say what it was for")
        @Size(max = 120, message = "Keep the description under 120 characters")
        String description,

        @NotNull(message = "Enter an amount")
        @Positive(message = "The amount must be more than zero")
        Long amountPaise,

        @NotNull(message = "Choose a date")
        LocalDate date,

        @NotNull(message = "Choose who paid")
        Long paidByMemberId,

        @NotNull(message = "Choose how to split it")
        SplitType splitType,

        @NotEmpty(message = "Choose who this expense is split between")
        List<@Valid @NotNull ShareRequest> shares) {

    public record ShareRequest(
            @NotNull(message = "Every share needs a member")
            Long memberId,
            Long value) {
    }
}
