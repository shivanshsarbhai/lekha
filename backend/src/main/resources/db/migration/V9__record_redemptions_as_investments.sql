-- Money coming back from an investment is now a positive investment piece, so invested totals are net of it. Pieces
-- saved as Transfer › Mutual fund redemption (or one of its sub-categories) become Investment › Mutual funds, keeping
-- their sign, and the transfer category is removed. A renamed redemption category is left alone; if Mutual funds has
-- been renamed, the pieces become uncategorised investments.

WITH redemption AS (
    SELECT id FROM categories
    WHERE kind = 'TRANSFER' AND parent_id IS NULL AND lower(name) = 'mutual fund redemption'
)
UPDATE allocations
SET kind        = 'INVESTMENT',
    category_id = (SELECT id FROM categories
                   WHERE kind = 'INVESTMENT' AND parent_id IS NULL AND lower(name) = 'mutual funds')
WHERE category_id IN (SELECT id FROM categories
                      WHERE id IN (SELECT id FROM redemption) OR parent_id IN (SELECT id FROM redemption));

DELETE FROM categories
WHERE parent_id IN (SELECT id FROM categories
                    WHERE kind = 'TRANSFER' AND parent_id IS NULL AND lower(name) = 'mutual fund redemption');

DELETE FROM categories
WHERE kind = 'TRANSFER' AND parent_id IS NULL AND lower(name) = 'mutual fund redemption';
