-- =========================
-- TRANSACTIONS  (transaction-service / TransactionService DB)
-- =========================
-- NOTE: Phase 4's seed data included DEPOSIT and WITHDRAW transactions too.
-- Those are dropped here on purpose — in Phase 5, deposit/withdraw only
-- update the wallet balance in account-service and no longer create a
-- Transaction/LedgerEntry row (deliberate simplification, see
-- payflow-project-context.md). Only TRANSFER rows are kept, since transfers
-- are still recorded here. This keeps the seed data consistent with what
-- the running app can actually produce.
--
-- wallet_ids referenced below (1, 2, 3) correspond to wallets seeded in
-- account-service-data.sql. There is no foreign key across databases —
-- these are just plain values now.

-- Rahul(1) -> Amit(2), 500
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(4, 1, 2, 500.00, 'TRANSFER', 'SUCCESS', 'Payment to Amit', CURRENT_TIMESTAMP);

-- Amit(2) -> Priya(3), 300
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(5, 2, 3, 300.00, 'TRANSFER', 'SUCCESS', 'Payment to Priya', CURRENT_TIMESTAMP);

-- Priya(3) -> Rahul(1), 200
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(6, 3, 1, 200.00, 'TRANSFER', 'SUCCESS', 'Payment to Rahul', CURRENT_TIMESTAMP);

-- Rahul(1) -> Priya(3), 1000
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(7, 1, 3, 1000.00, 'TRANSFER', 'SUCCESS', 'Payment to Priya', CURRENT_TIMESTAMP);

-- Priya(3) -> Amit(2), 400
INSERT INTO transactions
(id, from_wallet_id, to_wallet_id, amount, type, status, remarks, created_at)
VALUES
(9, 3, 2, 400.00, 'TRANSFER', 'SUCCESS', 'Payment to Amit', CURRENT_TIMESTAMP);


-- =========================
-- LEDGER ENTRIES
-- =========================
-- Matching DEBIT/CREDIT pair for each transfer above.

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

-- Txn 9: Priya(3) -> Amit(2), 400
INSERT INTO ledger_entries (id, wallet_id, transaction_id, amount, type, description, created_at)
VALUES
(13, 3, 9, 400.00, 'DEBIT',  'Transfer to wallet 2',   CURRENT_TIMESTAMP),
(14, 2, 9, 400.00, 'CREDIT', 'Transfer from wallet 3', CURRENT_TIMESTAMP);


-- =========================
-- BENEFICIARIES
-- =========================
-- user_id / beneficiary_wallet_id are plain values now (owned by
-- account-service), not foreign keys within this database.

INSERT INTO beneficiaries (id, user_id, beneficiary_wallet_id, nickname, created_at)
VALUES
(1, 1, 3, 'Priya',    CURRENT_TIMESTAMP),  -- Rahul saved Priya's wallet
(2, 2, 1, 'Rahul K.', CURRENT_TIMESTAMP);  -- Amit saved Rahul's wallet


-- =========================
-- RESYNC AUTO-INCREMENT SEQUENCES
-- =========================

SELECT setval(pg_get_serial_sequence('transactions', 'id'), (SELECT MAX(id) FROM transactions));
SELECT setval(pg_get_serial_sequence('ledger_entries', 'id'), (SELECT MAX(id) FROM ledger_entries));
SELECT setval(pg_get_serial_sequence('beneficiaries', 'id'), (SELECT MAX(id) FROM beneficiaries));
