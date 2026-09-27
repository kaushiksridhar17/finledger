package com.kaushiksridhar.finledger.transaction;

import java.time.Instant;
import java.time.LocalDate;

import com.kaushiksridhar.finledger.category.Category;
import com.kaushiksridhar.finledger.category.CategoryKind;

/** A transaction with the account and category names filled in, ready to show in a list. */
public record TransactionResponse(
        Long id,
        Long accountId,
        String accountName,
        Long categoryId,
        String categoryName,
        CategoryKind categoryKind,
        String categoryColor,
        long amountPaise,
        LocalDate date,
        String description,
        String merchant,
        String notes,
        Instant createdAt) {

    public static TransactionResponse from(Transaction t) {
        Category category = t.getCategory();
        return new TransactionResponse(
                t.getId(),
                t.getAccount().getId(),
                t.getAccount().getName(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                category != null ? category.getKind() : null,
                category != null ? category.getColor() : null,
                t.getAmountPaise(),
                t.getTxnDate(),
                t.getDescription(),
                t.getMerchant(),
                t.getNotes(),
                t.getCreatedAt());
    }
}
