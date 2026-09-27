package com.kaushiksridhar.finledger.account;

import java.time.Instant;

/** balancePaise = opening balance + every transaction in the account. It is calculated, never stored. */
public record AccountResponse(
        Long id,
        String name,
        AccountType type,
        long openingBalancePaise,
        long balancePaise,
        boolean archived,
        Instant createdAt) {

    public static AccountResponse from(Account account, long transactionsTotalPaise) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getOpeningBalancePaise(),
                account.getOpeningBalancePaise() + transactionsTotalPaise,
                account.getArchivedAt() != null,
                account.getCreatedAt());
    }
}
