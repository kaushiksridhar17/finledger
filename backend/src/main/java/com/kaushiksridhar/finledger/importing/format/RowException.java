package com.kaushiksridhar.finledger.importing.format;

/** Thrown while reading a single row. The parser records it as a RowError and carries on with the next row. */
public class RowException extends RuntimeException {

    public RowException(String message) {
        super(message);
    }
}
