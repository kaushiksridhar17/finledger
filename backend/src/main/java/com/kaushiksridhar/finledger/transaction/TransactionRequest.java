package com.kaushiksridhar.finledger.transaction;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** amountPaise is signed: -45000 is Rs 450 spent, 5000000 is Rs 50,000 received. */
public record TransactionRequest(

        @NotNull(message = "Account is required")
        Long accountId,

        Long categoryId,

        @NotNull(message = "Amount is required")
        Long amountPaise,

        @NotNull(message = "Date is required")
        LocalDate date,

        @NotBlank(message = "Description is required")
        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @Size(max = 100, message = "Merchant must be at most 100 characters")
        String merchant,

        @Size(max = 500, message = "Notes must be at most 500 characters")
        String notes) {
}
