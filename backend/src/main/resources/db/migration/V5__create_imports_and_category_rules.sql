-- Bank statement import: one row per uploaded file, with its progress and results
CREATE TABLE import_batches (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    account_id     BIGINT       NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    bank_format    VARCHAR(20)  NULL,                 -- HDFC, ICICI, SBI, TEMPLATE (filled once recognised)
    status         VARCHAR(20)  NOT NULL,             -- QUEUED, PROCESSING, COMPLETED, FAILED
    rows_total     INT          NOT NULL DEFAULT 0,
    rows_imported  INT          NOT NULL DEFAULT 0,
    rows_duplicate INT          NOT NULL DEFAULT 0,
    rows_failed    INT          NOT NULL DEFAULT 0,
    error_message  VARCHAR(500) NULL,                 -- why the whole file failed, if it did
    created_at     DATETIME(6)  NOT NULL,
    completed_at   DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_import_batches_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_import_batches_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    INDEX idx_import_batches_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Rows in a file that couldn't be read, so the user can see exactly which lines to fix
CREATE TABLE import_row_errors (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    batch_id    BIGINT       NOT NULL,
    line_number INT          NOT NULL,
    message     VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_import_row_errors_batch FOREIGN KEY (batch_id) REFERENCES import_batches (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Imported transactions remember which file they came from and carry a fingerprint.
-- The unique key means the same bank row can never be stored twice in one account,
-- even if the same statement (or an overlapping one) is uploaded again.
-- Manually added transactions leave dedupe_hash NULL; MySQL allows any number of NULLs in a unique key.
ALTER TABLE transactions
    ADD COLUMN import_batch_id BIGINT      NULL AFTER category_id,
    ADD COLUMN dedupe_hash     VARCHAR(64) NULL AFTER notes,
    ADD CONSTRAINT fk_transactions_import_batch FOREIGN KEY (import_batch_id) REFERENCES import_batches (id) ON DELETE SET NULL,
    ADD CONSTRAINT uk_transactions_account_dedupe UNIQUE (account_id, dedupe_hash);

-- A user's own auto-categorisation rules, e.g. "description contains CHAI POINT -> Food & Dining".
-- Checked oldest first, before the built-in merchant rules.
CREATE TABLE category_rules (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    category_id BIGINT       NOT NULL,
    match_type  VARCHAR(20)  NOT NULL,                -- CONTAINS, STARTS_WITH
    pattern     VARCHAR(100) NOT NULL,                -- stored in capitals; matching ignores case
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_category_rules_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_category_rules_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE,
    INDEX idx_category_rules_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
