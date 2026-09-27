package com.kaushiksridhar.finledger.budget;

public enum BudgetStatus {
    ON_TRACK,
    NEAR_LIMIT,
    OVER;

    /** 80% is the warning line. */
    public static BudgetStatus of(long spentPaise, long limitPaise) {
        if (spentPaise > limitPaise) {
            return OVER;
        }
        if (spentPaise * 100 >= limitPaise * BudgetService.WARNING_PERCENT) {
            return NEAR_LIMIT;
        }
        return ON_TRACK;
    }
}
