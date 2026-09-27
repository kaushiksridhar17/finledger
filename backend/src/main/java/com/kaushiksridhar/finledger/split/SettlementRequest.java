package com.kaushiksridhar.finledger.split;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** fromMemberId paid toMemberId back. */
public record SettlementRequest(

        @NotNull(message = "Choose who paid")
        Long fromMemberId,

        @NotNull(message = "Choose who was paid")
        Long toMemberId,

        @NotNull(message = "Enter an amount")
        @Positive(message = "The amount must be more than zero")
        Long amountPaise,

        @NotNull(message = "Choose a date")
        LocalDate date,

        @Size(max = 120, message = "Keep the note under 120 characters")
        String note) {
}
