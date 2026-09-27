package com.kaushiksridhar.finledger.importing.format;

/** The whole file can't be imported (unrecognised layout, too many rows...). The message is shown to the user. */
public class StatementFormatException extends RuntimeException {

    public StatementFormatException(String message) {
        super(message);
    }
}
