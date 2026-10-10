-- A refund is a positive expense piece that points at the purchase it gives money back for. Reports count it in the
-- purchase's month. If the purchase is deleted, the refund stays as an ordinary refund in its own month.
ALTER TABLE allocations ADD COLUMN refund_of UUID REFERENCES transactions (id) ON DELETE SET NULL;

ALTER TABLE allocations ADD CONSTRAINT allocations_refund_check
    CHECK (refund_of IS NULL OR (kind = 'EXPENSE' AND amount > 0 AND refund_of <> transaction_id));

CREATE INDEX allocations_refund_of_idx ON allocations (refund_of);
