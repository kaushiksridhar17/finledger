package com.kaushiksridhar.finledger.importing;

import java.time.Instant;
import java.util.List;

/** An import's progress and results. rowErrors is only filled in when one import is fetched on its own. */
public record ImportBatchResponse(
        Long id,
        Long accountId,
        String accountName,
        String fileName,
        String bankFormat,
        ImportStatus status,
        int rowsTotal,
        int rowsImported,
        int rowsDuplicate,
        int rowsFailed,
        String errorMessage,
        Instant createdAt,
        Instant completedAt,
        List<RowErrorResponse> rowErrors) {

    public record RowErrorResponse(int lineNumber, String message) {
    }

    public static ImportBatchResponse from(ImportBatch batch, List<RowErrorResponse> rowErrors) {
        return new ImportBatchResponse(
                batch.getId(),
                batch.getAccount().getId(),
                batch.getAccount().getName(),
                batch.getFileName(),
                batch.getBankFormat(),
                batch.getStatus(),
                batch.getRowsTotal(),
                batch.getRowsImported(),
                batch.getRowsDuplicate(),
                batch.getRowsFailed(),
                batch.getErrorMessage(),
                batch.getCreatedAt(),
                batch.getCompletedAt(),
                rowErrors);
    }
}
