package com.kaushiksridhar.finledger.recurring;

import java.util.List;

/** All detected series, plus what the confirmed bills and subscriptions cost per month in total. */
public record RecurringOverview(long confirmedMonthlyOutPaise, List<RecurringPaymentResponse> items) {
}
