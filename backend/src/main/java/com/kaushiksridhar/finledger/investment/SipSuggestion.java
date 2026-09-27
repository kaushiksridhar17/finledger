package com.kaushiksridhar.finledger.investment;

import java.time.LocalDate;

/**
 * Repeating debits in the bank statement that look like a SIP but aren't linked to a fund yet.
 * suggestedQuery is a guess at the fund's name, to start the search with.
 */
public record SipSuggestion(
        String matchKey,
        String label,
        int occurrences,
        long typicalAmountPaise,
        LocalDate lastDate,
        String suggestedQuery) {
}
