package com.kaushiksridhar.finledger.investment;

/** The NAV source couldn't answer: it's unreachable (notFound = false) or has no such fund (notFound = true). */
public class NavUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean notFound;

    public NavUnavailableException(String message, boolean notFound, Throwable cause) {
        super(message, cause);
        this.notFound = notFound;
    }

    public boolean isNotFound() {
        return notFound;
    }
}
