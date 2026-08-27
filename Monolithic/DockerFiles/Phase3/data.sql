-- =========================
-- USERS
-- =========================
-- Passwords below are BCrypt hashes of the plaintext "password123"
-- (all 3 users share the same plaintext password for local testing convenience).
-- role added for Phase 2 role-based access control.

INSERT INTO users (id, full_name, email, password, role, created_at)
VALUES
(1, 'Rahul Mulik', 'rahul@gmail.com', '$2b$12$Rc2sdxTcZzyjGdE/kUmcHOVwultUfvxnJEggfTexalgRWnz7d2Qq6', 'CUSTOMER', CURRENT_TIMESTAMP),
(2, 'Amit Sharma', 'amit@gmail.com',  '$2b$12$iEov5fRn8f1VFXvq5g/P7.5zBdVuJZJaioLF8BgpTQqldvFiisWV2', 'CUSTOMER', CURRENT_TIMESTAMP),
(3, 'Priya Patil', 'priya@gmail.com', '$2b$12$owuGOcctzwP2XrfHKoc8V.euLMAAhbwfVRpSkQ6iudmCs6triBgVa', 'CUSTOMER', CURRENT_TIMESTAMP);

-- Optional: a dedicated admin user for testing role-based access (Step 12 later).
-- Same plaintext password "password123" for convenience — change if you want.
INSERT INTO users (id, full_name, email, password, role, created_at)
VALUES
(4, 'Admin User', 'admin@gmail.com', '$2b$12$Rc2sdxTcZzyjGdE/kUmcHOVwultUfvxnJEggfTexalgRWnz7d2Qq6', 'ADMIN', CURRENT_TIMESTAMP);


-- =========================
-- WALLETS
-- =========================

INSERT INTO wallets
(id, user_id, balance, currency, version, created_at, updated_at)
VALUES
(1, 1, 3500.00, 'INR', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 2, 3100.00, 'INR', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 3, 2700.00, 'INR', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Admin has no wallet — matches real-world banking apps where an admin
-- account is an operator, not a customer with money. Adjust later if
-- your design wants every user to have one.


-- =========================
-- TRANSACTIONS
-- =========================

-- 1. Rahul deposits 5000
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(1, NULL, 1, 5000.00, 'DEPOSIT', 'SUCCESS',
 'Initial deposit', CURRENT_TIMESTAMP);


-- 2. Amit deposits 3000
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(2, NULL, 2, 3000.00, 'DEPOSIT', 'SUCCESS',
 'Initial deposit', CURRENT_TIMESTAMP);


-- 3. Priya deposits 2000
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(3, NULL, 3, 2000.00, 'DEPOSIT', 'SUCCESS',
 'Initial deposit', CURRENT_TIMESTAMP);


-- 4. Rahul sends 500 to Amit
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(4, 1, 2, 500.00, 'TRANSFER', 'SUCCESS',
 'Payment to Amit', CURRENT_TIMESTAMP);


-- 5. Amit sends 300 to Priya
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(5, 2, 3, 300.00, 'TRANSFER', 'SUCCESS',
 'Payment to Priya', CURRENT_TIMESTAMP);


-- 6. Priya sends 200 to Rahul
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(6, 3, 1, 200.00, 'TRANSFER', 'SUCCESS',
 'Payment to Rahul', CURRENT_TIMESTAMP);


-- 7. Rahul sends 1000 to Priya
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(7, 1, 3, 1000.00, 'TRANSFER', 'SUCCESS',
 'Payment to Priya', CURRENT_TIMESTAMP);


-- 8. Amit withdraws 500
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(8, 2, NULL, 500.00, 'WITHDRAW', 'SUCCESS',
 'Cash withdrawal', CURRENT_TIMESTAMP);


-- 9. Priya sends 400 to Amit
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(9, 3, 2, 400.00, 'TRANSFER', 'SUCCESS',
 'Payment to Amit', CURRENT_TIMESTAMP);


-- 10. Rahul withdraws 200
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(10, 1, NULL, 200.00, 'WITHDRAW', 'SUCCESS',
 'Cash withdrawal', CURRENT_TIMESTAMP);


-- =========================
-- LEDGER ENTRIES (Phase 3)
-- =========================
-- Every transaction above now has matching immutable ledger rows.
-- DEPOSIT  -> 1 CREDIT entry (money entering the wallet)
-- WITHDRAW -> 1 DEBIT entry  (money leaving the wallet)
-- TRANSFER -> 1 DEBIT (source) + 1 CREDIT (destination)

-- Txn 1: Rahul deposit 5000 -> CREDIT wallet 1
INSERT INTO ledger_entries (id, wallet_id, transaction_id, amount, type, description, created_at)
VALUES (1, 1, 1, 5000.00, 'CREDIT', 'Deposit', CURRENT_TIMESTAMP);

-- Txn 2: Amit deposit 3000 -> CREDIT wallet 2
INSERT INTO ledger_entries (id, wallet_id, transaction_id, amount, type, description, created_at)
VALUES (2, 2, 2, 3000.00, 'CREDIT', 'Deposit', CURRENT_TIMESTAMP);

-- Txn 3: Priya deposit 2000 -> CREDIT wallet 3
INSERT INTO ledger_entries (id, wallet_id, transaction_id, amount, type, description, created_at)
VALUES (3, 3, 3, 2000.00, 'CREDIT', 'Deposit', CURRENT_TIMESTAMP);

-- Txn 4: Rahul(1) -> Amit(2), 500
INSERT INTO ledger_entries (id, wallet_id, transaction_id, amount, type, description, created_at)
VALUES
(4, 1, 4, 500.00, 'DEBIT',  'Transfer to wallet 2',   CURRENT_TIMESTAMP),
(5, 2, 4, 500.00, 'CREDIT', 'Transfer from wallet 1', CURRENT_TIMESTAMP);

-- Txn 5: Amit(2) -> Priya(3), 300
INSERT INTO ledger_entries (id, wallet_id, transaction_id, amount, type, description, created_at)
VALUES
(6, 2, 5, 300.00, 'DEBIT',  'Transfer to wallet 3',   CURRENT_TIMESTAMP),
(7, 3, 5, 300.00, 'CREDIT', 'Transfer from wallet 2', CURRENT_TIMESTAMP);

-- Txn 6: Priya(3) -> Rahul(1), 200
INSERT INTO ledger_entries (id, wallet_id, transaction_id, amount, type, description, created_at)
VALUES
(8, 3, 6, 200.00, 'DEBIT',  'Transfer to wallet 1',   CURRENT_TIMESTAMP),
(9, 1, 6, 200.00, 'CREDIT', 'Transfer from wallet 3', CURRENT_TIMESTAMP);

-- Txn 7: Rahul(1) -> Priya(3), 1000
INSERT INTO ledger_entries (id, wallet_id, transaction_id, amount, type, description, created_at)
VALUES
(10, 1, 7, 1000.00, 'DEBIT',  'Transfer to wallet 3',   CURRENT_TIMESTAMP),
(11, 3, 7, 1000.00, 'CREDIT', 'Transfer from wallet 1', CURRENT_TIMESTAMP);

-- Txn 8: Amit withdraws 500 -> DEBIT wallet 2
INSERT INTO ledger_entries (id, wallet_id, transaction_id, amount, type, description, created_at)
VALUES (12, 2, 8, 500.00, 'DEBIT', 'Withdrawal', CURRENT_TIMESTAMP);

-- Txn 9: Priya(3) -> Amit(2), 400
INSERT INTO ledger_entries (id, wallet_id, transaction_id, amount, type, description, created_at)
VALUES
(13, 3, 9, 400.00, 'DEBIT',  'Transfer to wallet 2',   CURRENT_TIMESTAMP),
(14, 2, 9, 400.00, 'CREDIT', 'Transfer from wallet 3', CURRENT_TIMESTAMP);

-- Txn 10: Rahul withdraws 200 -> DEBIT wallet 1
INSERT INTO ledger_entries (id, wallet_id, transaction_id, amount, type, description, created_at)
VALUES (15, 1, 10, 200.00, 'DEBIT', 'Withdrawal', CURRENT_TIMESTAMP);


-- =========================
-- BENEFICIARIES (Phase 3)
-- =========================

INSERT INTO beneficiaries (id, user_id, beneficiary_wallet_id, nickname, created_at)
VALUES
(1, 1, 3, 'Priya',    CURRENT_TIMESTAMP),  -- Rahul saved Priya's wallet
(2, 2, 1, 'Rahul K.', CURRENT_TIMESTAMP);  -- Amit saved Rahul's wallet


-- =========================
-- RESYNC AUTO-INCREMENT SEQUENCES
-- =========================
-- Explicit IDs above bypass Postgres's IDENTITY sequence. Without this,
-- the next app-generated INSERT (e.g. registering a new user via the API)
-- collides with an existing seed row and fails with a duplicate-key error.

SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));
SELECT setval(pg_get_serial_sequence('wallets', 'id'), (SELECT MAX(id) FROM wallets));
SELECT setval(pg_get_serial_sequence('transactions', 'id'), (SELECT MAX(id) FROM transactions));
SELECT setval(pg_get_serial_sequence('ledger_entries', 'id'), (SELECT MAX(id) FROM ledger_entries));
SELECT setval(pg_get_serial_sequence('beneficiaries', 'id'), (SELECT MAX(id) FROM beneficiaries));