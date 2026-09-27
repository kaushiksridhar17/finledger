-- A monthly spending limit for one category. It applies to every month until changed.
CREATE TABLE budgets (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    category_id BIGINT      NOT NULL,
    limit_paise BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_budgets_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE,
    CONSTRAINT uk_budgets_user_category UNIQUE (user_id, category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Payments (and income) that repeat on a schedule, found by scanning transaction history.
-- The detector suggests them; the user confirms or dismisses each one.
CREATE TABLE recurring_payments (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    match_key        VARCHAR(120) NOT NULL,          -- what identifies the series, e.g. "M:NETFLIX"
    name             VARCHAR(100) NOT NULL,          -- shown to the user, e.g. "Netflix"
    direction        VARCHAR(3)   NOT NULL,          -- OUT (a bill) or IN (e.g. salary)
    frequency        VARCHAR(10)  NOT NULL,          -- WEEKLY, MONTHLY, QUARTERLY, YEARLY
    amount_paise     BIGINT       NOT NULL,          -- typical amount (median), always positive
    min_amount_paise BIGINT       NOT NULL,
    max_amount_paise BIGINT       NOT NULL,
    occurrences      INT          NOT NULL,
    category_id      BIGINT       NULL,
    account_id       BIGINT       NULL,
    last_seen_on     DATE         NOT NULL,
    next_due_on      DATE         NOT NULL,
    status           VARCHAR(12)  NOT NULL,          -- SUGGESTED, CONFIRMED, DISMISSED
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recurring_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL,
    CONSTRAINT fk_recurring_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE SET NULL,
    CONSTRAINT uk_recurring_user_key UNIQUE (user_id, match_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- In-app notifications. dedupe_key stops the same alert being sent twice,
-- e.g. "budget:12:2026-09:80" is the 80% warning for budget 12 in September 2026.
CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(30)  NOT NULL,
    title      VARCHAR(120) NOT NULL,
    message    VARCHAR(255) NOT NULL,
    link       VARCHAR(100) NULL,
    dedupe_key VARCHAR(120) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    read_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_notifications_user_key UNIQUE (user_id, dedupe_key),
    INDEX idx_notifications_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
