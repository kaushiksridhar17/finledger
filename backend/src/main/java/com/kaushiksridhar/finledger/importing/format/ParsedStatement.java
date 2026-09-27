package com.kaushiksridhar.finledger.importing.format;

import java.util.List;

/** The result of reading a statement: which bank layout it was, the good rows, and the lines that failed. */
public record ParsedStatement(String formatName, List<StatementRow> rows, List<RowError> errors) {

    public int totalRows() {
        return rows.size() + errors.size();
    }
}
