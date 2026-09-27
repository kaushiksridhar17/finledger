package com.kaushiksridhar.finledger.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for creating or editing an account.
 * archived is only used when editing: true hides the account, false brings it back, null leaves it as is.
 */
public record AccountRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotNull(message = "Type is required")
        AccountType type,

        @NotNull(message = "Opening balance is required")
        Long openingBalancePaise,

        Boolean archived) {
}
