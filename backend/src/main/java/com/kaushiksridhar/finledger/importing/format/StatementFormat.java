package com.kaushiksridhar.finledger.importing.format;

import java.util.List;

import com.kaushiksridhar.finledger.importing.csv.CsvReader.CsvRecord;

/**
 * Strategy for one bank's CSV layout. Each bank names and orders its columns differently,
 * so each layout knows how to recognise its own header row and how to read its rows.
 * StatementParser tries every known layout until one recognises the file.
 */
public interface StatementFormat {

    /** Short name stored with the import, e.g. "HDFC". */
    String name();

    /** header is already normalised: lower case, letters and digits only ("Withdrawal Amt." -> "withdrawalamt"). */
    boolean matchesHeader(List<String> header);

    /** Works out the column positions for this particular file once, then reads rows with them. */
    RowReader bind(List<String> header);

    interface RowReader {

        /** The raw text in the date column, used to spot where the transaction table ends. */
        String dateText(CsvRecord record);

        /** Reads one row, or throws RowException explaining what's wrong with it. */
        StatementRow read(CsvRecord record);
    }
}
