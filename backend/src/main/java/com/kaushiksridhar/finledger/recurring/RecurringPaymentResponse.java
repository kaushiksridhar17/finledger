package com.kaushiksridhar.finledger.recurring;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * daysUntilDue is negative when the expected date has passed without a payment showing up.
 * monthlyEquivalentPaise turns a yearly or weekly payment into its cost per month.
 */
public record RecurringPaymentResponse(
        Long id,
        String name,
        Direction direction,
        Frequency frequency,
        long amountPaise,
        long minAmountPaise,
        long maxAmountPaise,
        boolean amountVaries,
        int occurrences,
        Long categoryId,
        String categoryName,
        String categoryColor,
        String accountName,
        LocalDate lastSeenOn,
        LocalDate nextDueOn,
        long daysUntilDue,
        long monthlyEquivalentPaise,
        RecurringStatus status) {

    public static RecurringPaymentResponse from(RecurringPayment p, LocalDate today) {
        return new RecurringPaymentResponse(
                p.getId(),
                p.getName(),
                p.getDirection(),
                p.getFrequency(),
                p.getAmountPaise(),
                p.getMinAmountPaise(),
                p.getMaxAmountPaise(),
                p.getMinAmountPaise() != p.getMaxAmountPaise(),
                p.getOccurrences(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getCategory() != null ? p.getCategory().getColor() : null,
                p.getAccount() != null ? p.getAccount().getName() : null,
                p.getLastSeenOn(),
                p.getNextDueOn(),
                ChronoUnit.DAYS.between(today, p.getNextDueOn()),
                p.getFrequency().monthlyEquivalent(p.getAmountPaise()),
                p.getStatus());
    }
}
