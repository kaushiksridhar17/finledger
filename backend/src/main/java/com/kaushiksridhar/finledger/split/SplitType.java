package com.kaushiksridhar.finledger.split;

/** How an expense is divided between the people in it. */
public enum SplitType {
    /** Everyone pays the same. */
    EQUAL,
    /** Each person's amount is typed in, in paise. */
    EXACT,
    /** Each person pays a percentage, stored as basis points (33.33% = 3333). */
    PERCENT,
    /** Each person pays in proportion to a whole number of shares (e.g. 2 nights vs 1 night). */
    SHARES
}
