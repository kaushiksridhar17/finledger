package com.kaushiksridhar.finledger.importing.format;

/** A line that couldn't be read, with a message a person can act on. */
public record RowError(int lineNumber, String message) {
}
