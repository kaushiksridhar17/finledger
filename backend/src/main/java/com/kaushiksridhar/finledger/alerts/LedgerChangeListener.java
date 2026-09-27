package com.kaushiksridhar.finledger.alerts;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.kaushiksridhar.finledger.transaction.LedgerChangedEvent;

/**
 * Reacts to transactions being added, edited or deleted. It runs only after that change has been
 * committed, so it sees the new data and can never undo the user's save.
 */
@Component
public class LedgerChangeListener {

    private final AlertRefresher alertRefresher;

    public LedgerChangeListener(AlertRefresher alertRefresher) {
        this.alertRefresher = alertRefresher;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onLedgerChanged(LedgerChangedEvent event) {
        alertRefresher.refresh(event.userId(), event.fromImport());
    }
}
