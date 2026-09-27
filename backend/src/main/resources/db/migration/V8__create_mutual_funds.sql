-- Mutual funds. Scheme details and daily NAVs are public data from mfapi.in, cached here and shared by
-- every user. Holdings are built from each user's own purchases and redemptions.
-- Money is paise in BIGINT as everywhere else; NAVs (price per unit) and units are exact DECIMALs.

-- One row per fund, keyed by AMFI's scheme code (e.g. 122639 = Parag Parikh Flexi Cap - Direct - Growth)
CREATE TABLE mf_schemes (
    scheme_code     INT           NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    fund_house      VARCHAR(100)  NULL,
    category        VARCHAR(120)  NULL,
    latest_nav      DECIMAL(19,5) NULL,
    latest_nav_date DATE          NULL,
    refreshed_at    DATETIME(6)   NULL,
    PRIMARY KEY (scheme_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE mf_nav_history (
    scheme_code INT           NOT NULL,
    nav_date    DATE          NOT NULL,
    nav         DECIMAL(19,5) NOT NULL,
    PRIMARY KEY (scheme_code, nav_date),
    CONSTRAINT fk_mf_nav_scheme FOREIGN KEY (scheme_code) REFERENCES mf_schemes (scheme_code) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "The debits called NACH/BSE STARMF/SIP PARAG PARIKH FLEXI CAP are my SIP in fund 122639".
-- Every matching bank transaction becomes a purchase automatically, including future imports.
CREATE TABLE mf_sip_links (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    scheme_code INT          NOT NULL,
    match_key   VARCHAR(120) NOT NULL,           -- the debit's description with reference numbers removed
    label       VARCHAR(255) NOT NULL,           -- one real description, for showing to the user
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_mf_sip_links_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_mf_sip_links_scheme FOREIGN KEY (scheme_code) REFERENCES mf_schemes (scheme_code),
    CONSTRAINT uk_mf_sip_links_user_key UNIQUE (user_id, match_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Purchases (BUY) and redemptions (SELL). A purchase made from a linked SIP points at its bank
-- transaction, and disappears with it (or when the SIP is unlinked).
CREATE TABLE mf_transactions (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    user_id               BIGINT        NOT NULL,
    scheme_code           INT           NOT NULL,
    type                  VARCHAR(4)    NOT NULL,     -- BUY or SELL
    txn_date              DATE          NOT NULL,
    amount_paise          BIGINT        NOT NULL,     -- always positive
    units                 DECIMAL(19,3) NOT NULL,     -- always positive
    nav                   DECIMAL(19,5) NOT NULL,     -- the NAV the units were bought or sold at
    ledger_transaction_id BIGINT        NULL,
    sip_link_id           BIGINT        NULL,
    created_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_mf_txn_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_mf_txn_scheme FOREIGN KEY (scheme_code) REFERENCES mf_schemes (scheme_code),
    CONSTRAINT fk_mf_txn_ledger FOREIGN KEY (ledger_transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_mf_txn_sip_link FOREIGN KEY (sip_link_id) REFERENCES mf_sip_links (id) ON DELETE CASCADE,
    CONSTRAINT uk_mf_txn_ledger UNIQUE (ledger_transaction_id),
    INDEX idx_mf_txn_user_scheme (user_id, scheme_code, txn_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
