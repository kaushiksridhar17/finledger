package com.kaushiksridhar.finledger.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Creates, parses and hashes refresh tokens.
 * A refresh token looks like "42.xYz..." : the session id, a dot, then a random secret.
 */
final class RefreshTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokens() {
    }

    record Parsed(long sessionId, String secret) {
    }

    /** 32 random bytes (256 bits), URL-safe text. */
    static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 as 64 hex characters. Fits the VARCHAR(64) hash columns. */
    static String hash(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Compares two hashes in constant time, so an attacker can't learn how many
     * leading characters matched from how long the comparison took.
     */
    static boolean matches(String hashA, String hashB) {
        if (hashA == null || hashB == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hashA.getBytes(StandardCharsets.UTF_8),
                hashB.getBytes(StandardCharsets.UTF_8));
    }

    /** Splits "42.secret" into its parts. Empty if the value is missing or malformed. */
    static Optional<Parsed> parse(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        int dot = rawToken.indexOf('.');
        if (dot <= 0 || dot == rawToken.length() - 1) {
            return Optional.empty();
        }

        try {
            long sessionId = Long.parseLong(rawToken.substring(0, dot));
            String secret = rawToken.substring(dot + 1);
            return Optional.of(new Parsed(sessionId, secret));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
