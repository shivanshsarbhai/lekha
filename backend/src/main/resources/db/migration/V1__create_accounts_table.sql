CREATE TABLE accounts (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nickname    VARCHAR(60)  NOT NULL,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('BANK', 'CREDIT_CARD')),
    institution VARCHAR(20)  NOT NULL CHECK (institution IN ('HDFC', 'SBI', 'FEDERAL BANK')),
    last4       VARCHAR(4)   CHECK (last4 ~ '^[0-9]{4}$'),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
