package com.kaushiksridhar.finledger.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the logged-in user's id from the verified access token. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
