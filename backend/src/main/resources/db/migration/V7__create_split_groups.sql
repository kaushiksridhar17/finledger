-- Splitting shared expenses with friends (a trip, a flat). The user keeps track of the group;
-- friends don't need a FinLedger account, they are just names (with an optional UPI ID).
-- Every table hangs off split_groups with ON DELETE CASCADE, so deleting a group (or the user) removes everything.

CREATE TABLE split_groups (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(80) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_split_groups_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_split_groups_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- One row per person in a group. Exactly one per group has kind SELF: the user themselves.
CREATE TABLE group_members (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    group_id   BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,             -- as long as users.name, since the user is a member too
    upi_id     VARCHAR(100) NULL,                  -- e.g. rohan@okaxis, used for "Pay with UPI" links
    kind       VARCHAR(10)  NOT NULL,              -- SELF or FRIEND
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES split_groups (id) ON DELETE CASCADE,
    CONSTRAINT uk_group_members_name UNIQUE (group_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Something one person paid for on behalf of several people.
-- Member references cascade too; the app itself refuses to remove a member who appears in any expense.
CREATE TABLE group_expenses (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    group_id          BIGINT       NOT NULL,
    paid_by_member_id BIGINT       NOT NULL,
    description       VARCHAR(120) NOT NULL,
    amount_paise      BIGINT       NOT NULL,       -- always positive
    expense_date      DATE         NOT NULL,
    split_type        VARCHAR(10)  NOT NULL,       -- EQUAL, EXACT, PERCENT, SHARES
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_group_expenses_group FOREIGN KEY (group_id) REFERENCES split_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_expenses_payer FOREIGN KEY (paid_by_member_id) REFERENCES group_members (id) ON DELETE CASCADE,
    INDEX idx_group_expenses_group_date (group_id, expense_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Who owes what for one expense. The shares of an expense always add up to its amount exactly.
CREATE TABLE expense_shares (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    expense_id  BIGINT NOT NULL,
    member_id   BIGINT NOT NULL,
    share_paise BIGINT NOT NULL,
    input_value BIGINT NULL,                       -- what was typed: paise (EXACT), basis points (PERCENT), weight (SHARES)
    PRIMARY KEY (id),
    CONSTRAINT fk_expense_shares_expense FOREIGN KEY (expense_id) REFERENCES group_expenses (id) ON DELETE CASCADE,
    CONSTRAINT fk_expense_shares_member FOREIGN KEY (member_id) REFERENCES group_members (id) ON DELETE CASCADE,
    CONSTRAINT uk_expense_shares_member UNIQUE (expense_id, member_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Money paid back between two members. transaction_id links it to the bank transaction it was matched to.
CREATE TABLE settlements (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    group_id       BIGINT       NOT NULL,
    from_member_id BIGINT       NOT NULL,
    to_member_id   BIGINT       NOT NULL,
    amount_paise   BIGINT       NOT NULL,
    settled_on     DATE         NOT NULL,
    method         VARCHAR(10)  NOT NULL,          -- MANUAL or MATCHED
    transaction_id BIGINT       NULL,
    note           VARCHAR(120) NULL,
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_settlements_group FOREIGN KEY (group_id) REFERENCES split_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_settlements_from FOREIGN KEY (from_member_id) REFERENCES group_members (id) ON DELETE CASCADE,
    CONSTRAINT fk_settlements_to FOREIGN KEY (to_member_id) REFERENCES group_members (id) ON DELETE CASCADE,
    CONSTRAINT fk_settlements_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
    INDEX idx_settlements_group_date (group_id, settled_on)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "Rohan may have paid you back": money that came into the user's bank and looks like a friend settling up.
-- The unique key means a dismissed suggestion is never made again for the same transaction.
CREATE TABLE payment_matches (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    group_id       BIGINT      NOT NULL,
    member_id      BIGINT      NOT NULL,
    transaction_id BIGINT      NOT NULL,
    status         VARCHAR(10) NOT NULL,           -- SUGGESTED, ACCEPTED, DISMISSED
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_matches_group FOREIGN KEY (group_id) REFERENCES split_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_payment_matches_member FOREIGN KEY (member_id) REFERENCES group_members (id) ON DELETE CASCADE,
    CONSTRAINT fk_payment_matches_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    CONSTRAINT uk_payment_matches_member_txn UNIQUE (member_id, transaction_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
