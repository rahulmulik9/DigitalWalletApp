-- =========================
-- USERS  (account-service / AccountService DB)
-- =========================
-- Passwords below are BCrypt hashes of the plaintext "password123"
-- (all 3 users share the same plaintext password for local testing convenience).

INSERT INTO users (id, full_name, email, password, role, created_at)
VALUES
(1, 'Rahul Mulik', 'rahul@gmail.com', '$2b$12$Rc2sdxTcZzyjGdE/kUmcHOVwultUfvxnJEggfTexalgRWnz7d2Qq6', 'CUSTOMER', CURRENT_TIMESTAMP),
(2, 'Amit Sharma', 'amit@gmail.com',  '$2b$12$iEov5fRn8f1VFXvq5g/P7.5zBdVuJZJaioLF8BgpTQqldvFiisWV2', 'CUSTOMER', CURRENT_TIMESTAMP),
(3, 'Priya Patil', 'priya@gmail.com', '$2b$12$owuGOcctzwP2XrfHKoc8V.euLMAAhbwfVRpSkQ6iudmCs6triBgVa', 'CUSTOMER', CURRENT_TIMESTAMP);

-- Admin user for role-based access testing.
INSERT INTO users (id, full_name, email, password, role, created_at)
VALUES
(4, 'Admin User', 'admin@gmail.com', '$2b$12$Rc2sdxTcZzyjGdE/kUmcHOVwultUfvxnJEggfTexalgRWnz7d2Qq6', 'ADMIN', CURRENT_TIMESTAMP);


-- =========================
-- WALLETS
-- =========================
-- NOTE: balances below already reflect the net effect of the Phase 4 seed
-- transactions (deposits/transfers/withdrawals) that used to be recorded in
-- this same file. Since Phase 5 no longer stores those transaction rows in
-- account-service, we seed the FINAL balances directly instead of replaying
-- the history. See transaction-service-data.sql for the corresponding
-- transaction/ledger records (transfers only — deposits/withdrawals are not
-- recorded as transactions in Phase 5, per the deliberate simplification).

INSERT INTO wallets
(id, user_id, balance, currency, version, created_at, updated_at)
VALUES
(1, 1, 3500.00, 'INR', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 2, 3100.00, 'INR', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 3, 2700.00, 'INR', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Admin has no wallet — admin is an operator, not a customer with money.


-- =========================
-- RESYNC AUTO-INCREMENT SEQUENCES
-- =========================
-- Explicit IDs above bypass Postgres's IDENTITY sequence. Without this,
-- the next app-generated INSERT (e.g. registering a new user via the API)
-- collides with an existing seed row and fails with a duplicate-key error.

SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));
SELECT setval(pg_get_serial_sequence('wallets', 'id'), (SELECT MAX(id) FROM wallets));
