-- Core ledger: where money is kept (accounts), how it's labelled (categories), and every movement (transactions).
-- Money is always stored as whole paise in BIGINT columns, never as floating point.

CREATE TABLE accounts (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    type                  VARCHAR(20)  NOT NULL,             -- BANK, CASH, CREDIT_CARD, WALLET
    opening_balance_paise BIGINT       NOT NULL DEFAULT 0,
    archived_at           DATETIME(6)  NULL,                 -- set when hidden but kept for history
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_accounts_user_name UNIQUE (user_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE categories (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NULL,                             -- NULL = built-in category everyone sees
    name       VARCHAR(50) NOT NULL,
    kind       VARCHAR(10) NOT NULL,                         -- EXPENSE, INCOME, TRANSFER
    color      VARCHAR(7)  NOT NULL,                         -- hex colour for charts, e.g. #f97316
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_categories_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE transactions (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,                      -- copied from the account for fast per-user queries
    account_id   BIGINT       NOT NULL,
    category_id  BIGINT       NULL,
    amount_paise BIGINT       NOT NULL,                      -- signed: negative = money out, positive = money in
    txn_date     DATE         NOT NULL,
    description  VARCHAR(255) NOT NULL,
    merchant     VARCHAR(100) NULL,
    notes        VARCHAR(500) NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL,
    -- The transactions list is always "this user's, newest first", so index exactly that
    INDEX idx_transactions_user_date (user_id, txn_date, id),
    INDEX idx_transactions_account (account_id, txn_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Built-in categories shared by every user
INSERT INTO categories (user_id, name, kind, color, created_at) VALUES
    (NULL, 'Food & Dining',       'EXPENSE',  '#f97316', UTC_TIMESTAMP(6)),
    (NULL, 'Groceries',           'EXPENSE',  '#84cc16', UTC_TIMESTAMP(6)),
    (NULL, 'Rent',                'EXPENSE',  '#6366f1', UTC_TIMESTAMP(6)),
    (NULL, 'Bills & Utilities',   'EXPENSE',  '#0ea5e9', UTC_TIMESTAMP(6)),
    (NULL, 'Transport',           'EXPENSE',  '#eab308', UTC_TIMESTAMP(6)),
    (NULL, 'Shopping',            'EXPENSE',  '#ec4899', UTC_TIMESTAMP(6)),
    (NULL, 'Entertainment',       'EXPENSE',  '#a855f7', UTC_TIMESTAMP(6)),
    (NULL, 'Subscriptions',       'EXPENSE',  '#8b5cf6', UTC_TIMESTAMP(6)),
    (NULL, 'Health',              'EXPENSE',  '#ef4444', UTC_TIMESTAMP(6)),
    (NULL, 'Education',           'EXPENSE',  '#14b8a6', UTC_TIMESTAMP(6)),
    (NULL, 'Travel',              'EXPENSE',  '#06b6d4', UTC_TIMESTAMP(6)),
    (NULL, 'Personal Care',       'EXPENSE',  '#f43f5e', UTC_TIMESTAMP(6)),
    (NULL, 'Gifts & Donations',   'EXPENSE',  '#d946ef', UTC_TIMESTAMP(6)),
    (NULL, 'Other Expense',       'EXPENSE',  '#64748b', UTC_TIMESTAMP(6)),
    (NULL, 'Salary',              'INCOME',   '#10b981', UTC_TIMESTAMP(6)),
    (NULL, 'Freelance',           'INCOME',   '#22c55e', UTC_TIMESTAMP(6)),
    (NULL, 'Interest & Dividends','INCOME',   '#059669', UTC_TIMESTAMP(6)),
    (NULL, 'Refunds',             'INCOME',   '#65a30d', UTC_TIMESTAMP(6)),
    (NULL, 'Other Income',        'INCOME',   '#16a34a', UTC_TIMESTAMP(6)),
    (NULL, 'Transfer',            'TRANSFER', '#94a3b8', UTC_TIMESTAMP(6)),
    (NULL, 'Investment',          'TRANSFER', '#0d9488', UTC_TIMESTAMP(6)),
    (NULL, 'Credit Card Payment', 'TRANSFER', '#78716c', UTC_TIMESTAMP(6));
