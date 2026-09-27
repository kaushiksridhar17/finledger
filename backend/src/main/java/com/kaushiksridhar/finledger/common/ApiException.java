package com.kaushiksridhar.finledger.common;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Throw this anywhere in the app to send a specific HTTP status and message back to the client.
 * ApiExceptionHandler turns it into a JSON error response.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}