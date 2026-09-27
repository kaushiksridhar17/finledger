package com.kaushiksridhar.finledger.importing.format;

import java.time.LocalDate;

/** One successfully read line of a bank statement. amountPaise is signed: negative = money out. */
public record StatementRow(int lineNumber, LocalDate date, long amountPaise, String description) {
}
