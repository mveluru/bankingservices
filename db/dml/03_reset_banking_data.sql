-- =============================================================================
-- DML: DESTRUCTIVE - deletes ALL banking rows (withdrawal history, transactions, accounts,
-- customers, bank locations) but keeps the tables, and restarts their ids at 1. Does not touch any other table (the events table belongs to eventservice).
-- Rows are deleted children-first so foreign keys don't block it.
--
-- To get demo data back afterwards, either restart the app (AccountDataSeeder re-seeds when
-- the accounts / bank_locations tables are empty) or run db/data/01_seed_customers_accounts.sql
-- and db/data/04_seed_bank_locations.sql.
-- If the app is running, its 10-minute account-search cache may still show the old accounts
-- until it expires or the app restarts.
--
-- Usage:   mysql -u <user> -p < db/dml/03_reset_banking_data.sql
-- =============================================================================

USE db_example;

START TRANSACTION;
DELETE FROM withdrawal_history;
DELETE FROM account_transactions;
DELETE FROM accounts;
DELETE FROM customers;
DELETE FROM bank_location_services;
DELETE FROM bank_locations;
COMMIT;

-- ALTER TABLE ... AUTO_INCREMENT implicitly commits, so it runs after the deletes above.
ALTER TABLE withdrawal_history   AUTO_INCREMENT = 1;
ALTER TABLE account_transactions AUTO_INCREMENT = 1;
ALTER TABLE accounts             AUTO_INCREMENT = 1;
ALTER TABLE customers            AUTO_INCREMENT = 1;
ALTER TABLE bank_locations       AUTO_INCREMENT = 1;
