package com.kaushiksridhar.finledger.auth;

import java.time.Instant;

import com.kaushiksridhar.finledger.user.UserResponse;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserResponse user) {

    public static AuthResponse from(LoginResult result) {
        return new AuthResponse(
                result.accessToken(),
                "Bearer",
                result.accessTokenExpiresAt(),
                UserResponse.from(result.user()));
    }
}