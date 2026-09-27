package com.kaushiksridhar.finledger.transaction;

/**
 * Published whenever a user's transactions are added, edited or deleted.
 * Budget alerts listen for it, so the transaction code doesn't need to know about them.
 * fromImport asks for a full rescan for repeating payments as well (statement imports do this directly).
 */
public record LedgerChangedEvent(long userId, boolean fromImport) {
}
