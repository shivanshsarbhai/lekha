CREATE TABLE allocations (
    id             UUID            PRIMARY KEY,
    transaction_id UUID            NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    kind           VARCHAR(20)     NOT NULL CHECK (kind IN ('EXPENSE', 'INCOME', 'TRANSFER', 'INVESTMENT', 'LENT')),
    category_id    UUID            REFERENCES categories (id),
    amount         NUMERIC(14, 2)  NOT NULL CHECK (amount <> 0),
    note           VARCHAR(200),
    CONSTRAINT allocations_category_kind_check
        CHECK (category_id IS NULL OR kind IN ('EXPENSE', 'INCOME', 'INVESTMENT'))
);

CREATE INDEX allocations_transaction_idx ON allocations (transaction_id);

CREATE INDEX allocations_category_idx ON allocations (category_id);
