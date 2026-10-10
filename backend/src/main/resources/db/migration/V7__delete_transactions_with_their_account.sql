-- Deleting an account deletes its transactions, and through V4's cascade their allocations, in one statement.
ALTER TABLE transactions DROP CONSTRAINT transactions_account_id_fkey;
ALTER TABLE transactions ADD CONSTRAINT transactions_account_id_fkey
    FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE;
