package com.kaushiksridhar.finledger.category;

/**
 * EXPENSE and INCOME count towards spending and earning.
 * TRANSFER is money moving between your own pockets (bank to wallet, paying a credit card, an SIP),
 * so it is left out of spending totals.
 */
public enum CategoryKind {
    EXPENSE,
    INCOME,
    TRANSFER
}
