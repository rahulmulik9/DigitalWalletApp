-- =========================
-- USERS
-- =========================

INSERT INTO users (id, full_name, email, password, created_at)
VALUES
(1, 'Rahul Mulik', 'rahul@gmail.com', 'password123', CURRENT_TIMESTAMP),
(2, 'Amit Sharma', 'amit@gmail.com', 'password123', CURRENT_TIMESTAMP),
(3, 'Priya Patil', 'priya@gmail.com', 'password123', CURRENT_TIMESTAMP);


-- =========================
-- WALLETS
-- =========================

INSERT INTO wallets
(id, user_id, balance, currency, version, created_at, updated_at)
VALUES
(1, 1, 3500.00, 'USD', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 2, 3100.00, 'USD', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 3, 2700.00, 'USD', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);


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