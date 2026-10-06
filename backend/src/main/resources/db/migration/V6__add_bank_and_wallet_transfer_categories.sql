INSERT INTO categories (id, parent_id, name, kind, created_at)
VALUES (gen_random_uuid(), NULL, 'Bank to bank transfer', 'TRANSFER', now()),
       (gen_random_uuid(), NULL, 'Wallet to bank transfer', 'TRANSFER', now()),
       (gen_random_uuid(), NULL, 'Bank to wallet transfer', 'TRANSFER', now()),
       (gen_random_uuid(), NULL, 'Credit card bill payment', 'TRANSFER', now());
