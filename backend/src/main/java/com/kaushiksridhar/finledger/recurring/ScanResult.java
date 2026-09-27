package com.kaushiksridhar.finledger.recurring;

/** found: series currently detected. newSuggestions: how many of those are new since the last scan. */
public record ScanResult(int found, int newSuggestions) {
}
