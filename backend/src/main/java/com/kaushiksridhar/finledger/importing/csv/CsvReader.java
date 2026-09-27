package com.kaushiksridhar.finledger.importing.csv;

import java.util.ArrayList;
import java.util.List;

/**
 * A small RFC 4180 CSV reader, written by hand so every edge case is visible and tested:
 *  - fields in double quotes may contain commas and line breaks
 *  - "" inside a quoted field is one literal quote
 *  - Windows (\r\n), Unix (\n) and old Mac (\r) line endings
 *  - a UTF-8 byte order mark at the start (Excel adds one)
 *
 * Each record remembers the line it started on, so import errors can say "line 14".
 */
public final class CsvReader {

    private CsvReader() {
    }

    public record CsvRecord(int lineNumber, List<String> fields) {

        /** The field at index, or "" if the row is shorter than that. */
        public String field(int index) {
            return index >= 0 && index < fields.size() ? fields.get(index) : "";
        }

        public boolean isBlank() {
            return fields.stream().allMatch(String::isBlank);
        }
    }

    public static List<CsvRecord> read(String text) {
        String input = text.startsWith("\uFEFF") ? text.substring(1) : text;

        List<CsvRecord> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();

        boolean inQuotes = false;
        boolean recordHasContent = false;
        int line = 1;
        int recordStartLine = 1;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    boolean escapedQuote = i + 1 < input.length() && input.charAt(i + 1) == '"';
                    if (escapedQuote) {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    if (c == '\n' || (c == '\r' && !(i + 1 < input.length() && input.charAt(i + 1) == '\n'))) {
                        line++;
                    }
                    field.append(c);
                }
                continue;
            }

            switch (c) {
                case '"' -> {
                    inQuotes = true;
                    recordHasContent = true;
                }
                case ',' -> {
                    fields.add(field.toString());
                    field.setLength(0);
                    recordHasContent = true;
                }
                case '\r', '\n' -> {
                    if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') {
                        i++;
                    }
                    if (recordHasContent || field.length() > 0) {
                        fields.add(field.toString());
                        records.add(new CsvRecord(recordStartLine, List.copyOf(fields)));
                    }
                    fields.clear();
                    field.setLength(0);
                    recordHasContent = false;
                    line++;
                    recordStartLine = line;
                }
                default -> {
                    field.append(c);
                    recordHasContent = true;
                }
            }
        }

        // Last record when the file doesn't end with a line break
        if (recordHasContent || field.length() > 0) {
            fields.add(field.toString());
            records.add(new CsvRecord(recordStartLine, List.copyOf(fields)));
        }

        return records;
    }
}
