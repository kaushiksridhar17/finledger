package com.kaushiksridhar.finledger.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kaushiksridhar.finledger.importing.format.StatementRow;

class DedupeHashesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 7);

    @Test
    @DisplayName("the same rows always give the same fingerprints")
    void stable() {
        List<StatementRow> rows = List.of(row(1, -2_000, "UPI/CHAI POINT"), row(2, -43_200, "UPI/SWIGGY"));

        assertThat(DedupeHashes.compute(5, rows)).isEqualTo(DedupeHashes.compute(5, rows));
    }

    @Test
    @DisplayName("two identical rows in one file are both kept, as occurrence 1 and occurrence 2")
    void identicalRowsDiffer() {
        List<String> hashes = DedupeHashes.compute(5, List.of(
                row(1, -2_000, "UPI/CHAI POINT"),
                row(2, -2_000, "UPI/CHAI POINT")));

        assertThat(new HashSet<>(hashes)).hasSize(2);
    }

    @Test
    @DisplayName("differences in capitals and spacing don't change the fingerprint")
    void normalisesDescription() {
        String a = DedupeHashes.compute(5, List.of(row(1, -2_000, "UPI/Chai  Point "))).get(0);
        String b = DedupeHashes.compute(5, List.of(row(9, -2_000, "upi/chai point"))).get(0);

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("the same row in a different account is a different fingerprint")
    void accountMatters() {
        List<StatementRow> rows = List.of(row(1, -2_000, "UPI/CHAI POINT"));

        assertThat(DedupeHashes.compute(5, rows)).isNotEqualTo(DedupeHashes.compute(6, rows));
    }

    private static StatementRow row(int line, long amountPaise, String description) {
        return new StatementRow(line, DAY, amountPaise, description);
    }
}
