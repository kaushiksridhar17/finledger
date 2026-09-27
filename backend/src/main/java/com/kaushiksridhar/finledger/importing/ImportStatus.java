package com.kaushiksridhar.finledger.importing;

public enum ImportStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean isFinished() {
        return this == COMPLETED || this == FAILED;
    }
}
