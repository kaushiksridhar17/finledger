package com.kaushiksridhar.finledger.importing.format;

import java.util.List;

import com.kaushiksridhar.finledger.importing.csv.CsvReader.CsvRecord;

/** A single amount column where negative means money out, e.g. the FinLedger template: Date, Description, Amount. */
public class SignedAmountFormat extends BaseFormat {

    private final String dateColumn;
    private final String descriptionColumn;
    private final String amountColumn;

    public SignedAmountFormat(String name, String dateColumn, String descriptionColumn, String amountColumn,
            String... datePatterns) {
        super(name, datePatterns);
        this.dateColumn = dateColumn;
        this.descriptionColumn = descriptionColumn;
        this.amountColumn = amountColumn;
    }

    @Override
    public boolean matchesHeader(List<String> header) {
        return header.containsAll(List.of(dateColumn, descriptionColumn, amountColumn));
    }

    @Override
    public RowReader bind(List<String> header) {
        int date = column(header, dateColumn);
        int description = column(header, descriptionColumn);
        int amount = column(header, amountColumn);

        return new RowReader() {
            @Override
            public String dateText(CsvRecord record) {
                return record.field(date);
            }

            @Override
            public StatementRow read(CsvRecord record) {
                Long amountPaise = parseAmount(record.field(amount));
                if (amountPaise == null || amountPaise == 0) {
                    throw new RowException("No amount");
                }
                return new StatementRow(
                        record.lineNumber(),
                        parseDate(record.field(date)),
                        amountPaise,
                        cleanDescription(record.field(description)));
            }
        };
    }
}
