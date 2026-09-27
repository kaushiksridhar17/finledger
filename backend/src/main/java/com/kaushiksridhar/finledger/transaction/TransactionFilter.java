package com.kaushiksridhar.finledger.transaction;

import java.time.LocalDate;

/** Optional filters for the transactions list. Any field left null is ignored. */
public record TransactionFilter(
        Long accountId,
        Long categoryId,
        LocalDate from,
        LocalDate to,
        String direction,
        String search) {
}
