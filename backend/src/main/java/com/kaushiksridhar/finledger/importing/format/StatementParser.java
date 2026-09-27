package com.kaushiksridhar.finledger.importing.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.kaushiksridhar.finledger.importing.csv.CsvReader;
import com.kaushiksridhar.finledger.importing.csv.CsvReader.CsvRecord;
import com.kaushiksridhar.finledger.importing.format.StatementFormat.RowReader;

/**
 * Turns the text of a bank statement CSV into rows.
 *
 * Real bank exports aren't clean tables. They start with account details, may put a line of
 * asterisks under the header, and end with a summary block. So the parser:
 *  1. looks through the first lines for a header row that some known layout recognises
 *  2. reads rows after it, skipping blank and decoration lines
 *  3. stops at the first row without a date once transactions have started (the summary)
 *  4. collects bad rows as errors instead of failing the whole file
 */
public final class StatementParser {

    static final int HEADER_SEARCH_LINES = 40;
    static final int MAX_ROWS = 5_000;

    static final String UNRECOGNISED = "We couldn't recognise this file. FinLedger reads HDFC, ICICI and SBI "
            + "statement CSVs, or the FinLedger template with Date, Description and Amount columns.";

    private StatementParser() {
    }

    public static ParsedStatement parse(String text) {
        List<CsvRecord> records = CsvReader.read(text);

        int headerIndex = -1;
        StatementFormat format = null;
        List<String> header = List.of();

        for (int i = 0; i < Math.min(records.size(), HEADER_SEARCH_LINES) && format == null; i++) {
            List<String> candidate = normaliseHeader(records.get(i));
            for (StatementFormat f : StatementFormats.ALL) {
                if (f.matchesHeader(candidate)) {
                    format = f;
                    header = candidate;
                    headerIndex = i;
                    break;
                }
            }
        }

        if (format == null) {
            throw new StatementFormatException(UNRECOGNISED);
        }

        RowReader reader = format.bind(header);
        List<StatementRow> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();

        for (int i = headerIndex + 1; i < records.size(); i++) {
            CsvRecord record = records.get(i);
            if (isDecoration(record)) {
                continue;
            }

            String dateText = reader.dateText(record).trim();
            if (!containsDigit(dateText)) {
                boolean tableStarted = !rows.isEmpty() || !errors.isEmpty();
                if (tableStarted) {
                    break;          // reached the summary block at the end of the statement
                }
                continue;           // something between the header and the first transaction
            }

            if (rows.size() + errors.size() >= MAX_ROWS) {
                throw new StatementFormatException("This file has more than " + MAX_ROWS
                        + " transactions. Please split it into smaller statements.");
            }

            try {
                rows.add(reader.read(record));
            } catch (RowException e) {
                errors.add(new RowError(record.lineNumber(), e.getMessage()));
            }
        }

        if (rows.isEmpty() && errors.isEmpty()) {
            throw new StatementFormatException("The file has a " + format.name()
                    + " header but no transactions under it.");
        }

        return new ParsedStatement(format.name(), List.copyOf(rows), List.copyOf(errors));
    }

    /** "Withdrawal Amount (INR )" -> "withdrawalamountinr" */
    static List<String> normaliseHeader(CsvRecord record) {
        return record.fields().stream()
                .map(field -> field.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""))
                .toList();
    }

    /** Blank lines and separator lines like "********,********,..." */
    private static boolean isDecoration(CsvRecord record) {
        return record.fields().stream().allMatch(field -> field.trim().matches("[*\\-=_ ]*"));
    }

    private static boolean containsDigit(String text) {
        return text.chars().anyMatch(Character::isDigit);
    }
}
