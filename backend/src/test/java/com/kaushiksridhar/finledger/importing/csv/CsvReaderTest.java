package com.kaushiksridhar.finledger.importing.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kaushiksridhar.finledger.importing.csv.CsvReader.CsvRecord;

class CsvReaderTest {

    @Test
    @DisplayName("splits plain fields on commas")
    void plainFields() {
        List<CsvRecord> records = CsvReader.read("a,b,c\n1,2,3\n");

        assertThat(records).hasSize(2);
        assertThat(records.get(1).fields()).containsExactly("1", "2", "3");
    }

    @Test
    @DisplayName("quoted fields keep commas, and a doubled quote is one literal quote")
    void quotedFields() {
        List<CsvRecord> records = CsvReader.read("\"Dinner at Truffles, Koramangala\",\"he said \"\"hi\"\"\"\n");

        assertThat(records.get(0).fields()).containsExactly("Dinner at Truffles, Koramangala", "he said \"hi\"");
    }

    @Test
    @DisplayName("a quoted field can span lines, and line numbers stay correct after it")
    void multiLineField() {
        List<CsvRecord> records = CsvReader.read("\"two\nlines\",x\nnext,row\n");

        assertThat(records.get(0).fields()).containsExactly("two\nlines", "x");
        assertThat(records.get(0).lineNumber()).isEqualTo(1);
        assertThat(records.get(1).lineNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("handles Windows line endings, a byte order mark, blank lines and a missing final newline")
    void messyInput() {
        List<CsvRecord> records = CsvReader.read("\uFEFFDate,Amount\r\n\r\n2026-09-01,100\r\nlast,row");

        assertThat(records).hasSize(3);
        assertThat(records.get(0).fields()).containsExactly("Date", "Amount");
        assertThat(records.get(1).lineNumber()).isEqualTo(3);
        assertThat(records.get(2).fields()).containsExactly("last", "row");
    }

    @Test
    @DisplayName("empty fields are kept, and field() returns blank past the end of a short row")
    void emptyFields() {
        CsvRecord record = CsvReader.read("a,,c,\n").get(0);

        assertThat(record.fields()).containsExactly("a", "", "c", "");
        assertThat(record.field(10)).isEmpty();
        assertThat(CsvReader.read(",,,\n").get(0).isBlank()).isTrue();
    }
}
