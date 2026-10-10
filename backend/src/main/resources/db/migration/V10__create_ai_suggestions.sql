-- Trigram similarity finds past transactions whose descriptions look like a new one, to show the model as examples.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- What the language model suggests for an unclassified transaction. It is only ever a suggestion: classifying the
-- transaction deletes it. Deleting the category deletes the suggestions that point at it.
CREATE TABLE ai_suggestions (
    transaction_id UUID          PRIMARY KEY REFERENCES transactions (id) ON DELETE CASCADE,
    kind           VARCHAR(20)   NOT NULL CHECK (kind IN ('EXPENSE', 'INCOME', 'TRANSFER', 'INVESTMENT', 'LENT')),
    category_id    UUID          REFERENCES categories (id) ON DELETE CASCADE,
    reason         VARCHAR(300)  NOT NULL,
    model          VARCHAR(100)  NOT NULL,
    suggested_at   TIMESTAMPTZ   NOT NULL
);
