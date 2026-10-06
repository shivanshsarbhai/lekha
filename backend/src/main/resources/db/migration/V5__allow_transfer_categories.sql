-- Transfers can now be categorised too, e.g. to tell a mutual fund redemption apart from moving money between
-- your own accounts. Lent stays the only kind without categories.

ALTER TABLE categories DROP CONSTRAINT categories_kind_check;
ALTER TABLE categories ADD CONSTRAINT categories_kind_check
    CHECK (kind IN ('EXPENSE', 'INCOME', 'INVESTMENT', 'TRANSFER'));

ALTER TABLE allocations DROP CONSTRAINT allocations_category_kind_check;
ALTER TABLE allocations ADD CONSTRAINT allocations_category_kind_check
    CHECK (category_id IS NULL OR kind IN ('EXPENSE', 'INCOME', 'INVESTMENT', 'TRANSFER'));

INSERT INTO categories (id, parent_id, name, kind, created_at)
VALUES (gen_random_uuid(), NULL, 'Mutual fund redemption', 'TRANSFER', now());
