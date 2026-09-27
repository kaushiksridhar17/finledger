package com.kaushiksridhar.finledger.auth;

import java.time.Instant;

import com.kaushiksridhar.finledger.user.User;

public record LoginResult(
        User user,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {
}