package com.kaushiksridhar.finledger.importing.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reads the sample statements in src/test/resources/statements, one per bank layout. */
class StatementParserTest {

    @Test
    @DisplayName("HDFC: skips the account details and asterisk lines, stops at the summary, reports bad rows")
    void hdfc() throws IOException {
        ParsedStatement statement = StatementParser.parse(resource("hdfc-august.csv"));

        assertThat(statement.formatName()).isEqualTo("HDFC");
        assertThat(statement.rows()).hasSize(27);
        assertThat(statement.totalRows()).isEqualTo(29);

        StatementRow salary = statement.rows().get(0);
        assertThat(salary.date()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(salary.amountPaise()).isEqualTo(9_200_000);
        assertThat(salary.description()).isEqualTo("SALARY ACME TECH PVT LTD AUG2026");

        StatementRow swiggy = statement.rows().get(5);
        assertThat(swiggy.amountPaise()).isEqualTo(-43_200);

        assertThat(statement.errors()).extracting(RowError::message).containsExactly(
                "Couldn't read the date \"32/08/26\"",
                "No amount in either the withdrawal or the deposit column");
    }

    @Test
    @DisplayName("ICICI: 0.00 in the unused column counts as empty; uses the transaction date column")
    void icici() throws IOException {
        ParsedStatement statement = StatementParser.parse(resource("icici.csv"));

        assertThat(statement.formatName()).isEqualTo("ICICI");
        assertThat(statement.rows()).hasSize(11);
        assertThat(statement.errors()).isEmpty();
        assertThat(statement.rows().get(0).amountPaise()).isEqualTo(-64_900);
        assertThat(statement.rows().get(7).amountPaise()).isEqualTo(1_845_600);
    }

    @Test
    @DisplayName("SBI: dates like \"1 Sep 2026\" and amounts with Indian commas like \"92,000.00\"")
    void sbi() throws IOException {
        ParsedStatement statement = StatementParser.parse(resource("sbi.csv"));

        assertThat(statement.formatName()).isEqualTo("SBI");
        assertThat(statement.rows()).hasSize(9);
        assertThat(statement.rows().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(statement.rows().get(0).amountPaise()).isEqualTo(9_200_000);
        assertThat(statement.rows().get(2).amountPaise()).isEqualTo(-300_000);
    }

    @Test
    @DisplayName("FinLedger template: one signed amount column, quoted descriptions with commas")
    void template() throws IOException {
        ParsedStatement statement = StatementParser.parse(resource("template.csv"));

        assertThat(statement.formatName()).isEqualTo("TEMPLATE");
        assertThat(statement.rows()).hasSize(8);
        assertThat(statement.rows().get(7).description()).isEqualTo("Dinner at Truffles, Koramangala");
        assertThat(statement.rows().get(7).amountPaise()).isEqualTo(-185_050);
    }

    @Test
    @DisplayName("impossible dates, bad amounts and too many decimals become row errors, not crashes")
    void badRows() {
        ParsedStatement statement = StatementParser.parse("""
                Date,Description,Amount
                2026-02-31,Impossible date,-100
                2026-09-01,Bad amount,abc
                2026-09-02,Too precise,-1.234
                2026-09-03,,-50
                2026-09-04,Fine,-99.9
                """);

        assertThat(statement.rows()).hasSize(1);
        assertThat(statement.rows().get(0).amountPaise()).isEqualTo(-9_990);
        assertThat(statement.errors()).extracting(RowError::lineNumber).containsExactly(2, 3, 4, 5);
    }

    @Test
    @DisplayName("a file with no recognisable header is rejected with a helpful message")
    void unrecognised() {
        assertThatThrownBy(() -> StatementParser.parse("name,age\nArjun,25\n"))
                .isInstanceOf(StatementFormatException.class)
                .hasMessageContaining("HDFC, ICICI and SBI");
    }

    @Test
    @DisplayName("a header with no transactions under it is rejected")
    void headerOnly() {
        assertThatThrownBy(() -> StatementParser.parse("Date,Description,Amount\n"))
                .isInstanceOf(StatementFormatException.class)
                .hasMessageContaining("no transactions");
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = StatementParserTest.class.getResourceAsStream("/statements/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
