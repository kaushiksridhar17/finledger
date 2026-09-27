package com.kaushiksridhar.finledger.importing.format;

import java.util.List;

/**
 * Every layout FinLedger can read. Adding a bank is one more line here.
 * Column names are written normalised (lower case, letters and digits only).
 * Date patterns use "uuuu"/"uu" for the year, which strict parsing requires.
 */
public final class StatementFormats {

    public static final StatementFormat HDFC = new DebitCreditFormat("HDFC",
            "date", "narration", "withdrawalamt", "depositamt",
            "dd/MM/uu", "dd/MM/uuuu");

    public static final StatementFormat ICICI = new DebitCreditFormat("ICICI",
            "transactiondate", "transactionremarks", "withdrawalamountinr", "depositamountinr",
            "dd/MM/uuuu", "dd-MM-uuuu");

    public static final StatementFormat SBI = new DebitCreditFormat("SBI",
            "txndate", "description", "debit", "credit",
            "d MMM uuuu", "dd/MM/uuuu", "dd-MM-uuuu");

    public static final StatementFormat TEMPLATE = new SignedAmountFormat("TEMPLATE",
            "date", "description", "amount",
            "uuuu-MM-dd", "dd/MM/uuuu", "dd-MM-uuuu");

    // Checked in this order. More specific layouts come before the simple template.
    public static final List<StatementFormat> ALL = List.of(HDFC, ICICI, SBI, TEMPLATE);

    private StatementFormats() {
    }
}
