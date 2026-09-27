package com.kaushiksridhar.finledger.importing.format;

import java.util.List;

import com.kaushiksridhar.finledger.importing.csv.CsvReader.CsvRecord;

/**
 * The usual Indian bank layout: money out in a "withdrawal"/"debit" column and money in
 * in a separate "deposit"/"credit" column, each row filling exactly one of them.
 */
public class DebitCreditFormat extends BaseFormat {

    private final String dateColumn;
    private final String descriptionColumn;
    private final String debitColumn;
    private final String creditColumn;

    public DebitCreditFormat(String name, String dateColumn, String descriptionColumn,
            String debitColumn, String creditColumn, String... datePatterns) {
        super(name, datePatterns);
        this.dateColumn = dateColumn;
        this.descriptionColumn = descriptionColumn;
        this.debitColumn = debitColumn;
        this.creditColumn = creditColumn;
    }

    @Override
    public boolean matchesHeader(List<String> header) {
        return header.containsAll(List.of(dateColumn, descriptionColumn, debitColumn, creditColumn));
    }

    @Override
    public RowReader bind(List<String> header) {
        int date = column(header, dateColumn);
        int description = column(header, descriptionColumn);
        int debit = column(header, debitColumn);
        int credit = column(header, creditColumn);

        return new RowReader() {
            @Override
            public String dateText(CsvRecord record) {
                return record.field(date);
            }

            @Override
            public StatementRow read(CsvRecord record) {
                Long debitPaise = parseAmount(record.field(debit));
                Long creditPaise = parseAmount(record.field(credit));
                long out = debitPaise == null ? 0 : debitPaise;
                long in = creditPaise == null ? 0 : creditPaise;

                if (out < 0 || in < 0) {
                    throw new RowException("Negative number in the withdrawal or deposit column");
                }
                if (out == 0 && in == 0) {
                    throw new RowException("No amount in either the withdrawal or the deposit column");
                }
                if (out > 0 && in > 0) {
                    throw new RowException("Both a withdrawal and a deposit are filled in");
                }

                return new StatementRow(
                        record.lineNumber(),
                        parseDate(record.field(date)),
                        in > 0 ? in : -out,
                        cleanDescription(record.field(description)));
            }
        };
    }
}
