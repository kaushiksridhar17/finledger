package com.kaushiksridhar.finledger.user;

import java.time.Instant;

/** The public view of a user. Never includes the password hash. */
public record UserResponse(
        Long id,
        String name,
        String email,
        String baseCurrency,
        boolean demo,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getBaseCurrency(),
                user.isDemo(),
                user.getCreatedAt());
    }
}
