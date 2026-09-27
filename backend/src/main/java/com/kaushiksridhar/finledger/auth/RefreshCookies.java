package com.kaushiksridhar.finledger.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.kaushiksridhar.finledger.security.AuthProperties;

@Component
public class RefreshCookies {

    public static final String NAME = "finledger_refresh";
    private static final String PATH = "/api/auth";

    private final AuthProperties properties;
    private final Clock clock;

    public RefreshCookies(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public ResponseCookie create(String refreshToken, Instant expiresAt) {
        return ResponseCookie.from(NAME, refreshToken)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(Duration.between(clock.instant(), expiresAt))
                .build();
    }

    /** An already-expired cookie with the same name and path tells the browser to delete it. */
    public ResponseCookie clear() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(0)
                .build();
    }
}