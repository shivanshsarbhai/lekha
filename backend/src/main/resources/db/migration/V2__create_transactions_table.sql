CREATE TABLE transactions (
    id               UUID            PRIMARY KEY,
    account_id       UUID            NOT NULL REFERENCES accounts (id),
    transaction_date DATE            NOT NULL,
    settlement_date  DATE,
    description      TEXT            NOT NULL,
    amount           NUMERIC(14, 2)  NOT NULL CHECK (amount <> 0),
    balance_after    NUMERIC(14, 2),
    payment_mode     VARCHAR(20)     CHECK (payment_mode IN ('UPI', 'CARD', 'NEFT', 'IMPS', 'RTGS', 'ATM', 'CHEQUE', 'OTHER')),
    metadata         JSONB           NOT NULL,
    created_at       TIMESTAMPTZ     NOT NULL
);

CREATE INDEX transactions_account_date_idx ON transactions (account_id, transaction_date);
