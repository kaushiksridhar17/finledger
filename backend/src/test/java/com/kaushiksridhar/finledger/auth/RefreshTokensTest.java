package com.kaushiksridhar.finledger.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Plain unit tests: no Spring, no database, run in milliseconds. */
class RefreshTokensTest {

    @Test
    @DisplayName("new secrets are 43 URL-safe characters and never repeat")
    void newSecretIsRandomAndUrlSafe() {
        String a = RefreshTokens.newSecret();
        String b = RefreshTokens.newSecret();

        assertThat(a).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("hash is 64 hex characters, the same for the same input, and different for different input")
    void hashIsStableSha256Hex() {
        String hash = RefreshTokens.hash("secret");

        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
        assertThat(RefreshTokens.hash("secret")).isEqualTo(hash);
        assertThat(RefreshTokens.hash("secret2")).isNotEqualTo(hash);
    }

    @Test
    @DisplayName("matches compares hashes and treats null as no match")
    void matchesHandlesNulls() {
        String hash = RefreshTokens.hash("secret");

        assertThat(RefreshTokens.matches(hash, RefreshTokens.hash("secret"))).isTrue();
        assertThat(RefreshTokens.matches(hash, RefreshTokens.hash("other"))).isFalse();
        assertThat(RefreshTokens.matches(hash, null)).isFalse();
        assertThat(RefreshTokens.matches(null, hash)).isFalse();
    }

    @Test
    @DisplayName("parse splits a well-formed token into session id and secret")
    void parseValidToken() {
        RefreshTokens.Parsed parsed = RefreshTokens.parse("42.abcDEF_123").orElseThrow();

        assertThat(parsed.sessionId()).isEqualTo(42L);
        assertThat(parsed.secret()).isEqualTo("abcDEF_123");
    }

    @ParameterizedTest(name = "\"{0}\" is rejected")
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "abc", ".abc", "42.", "x.abc", "4 2.abc" })
    @DisplayName("parse rejects missing or malformed tokens")
    void parseRejectsMalformedTokens(String raw) {
        assertThat(RefreshTokens.parse(raw)).isEmpty();
    }
}
