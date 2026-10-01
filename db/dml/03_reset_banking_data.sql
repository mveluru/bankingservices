-- =============================================================================
-- DML: DESTRUCTIVE - deletes ALL banking rows (withdrawal history, transactions, accounts,
-- customers, bank locations, bank employees and both login tables) but keeps the tables, and restarts their ids at 1. Does not touch any other table (the events table belongs to eventservice).
-- Rows are deleted children-first so foreign keys don't block it.
--
-- Logins are deleted with their customers/employees on purpose: they reference those ids, and the ids restart
-- at 1, so surviving logins would point at the wrong people.
--
-- To get demo data back afterwards, either restart the app (the seeders re-seed when their tables are empty)
-- or run db/data/01, 04, 07, 08 and 09 (see db/README.md).
-- If the app is running, its 10-minute account-search cache may still show the old accounts
-- until it expires or the app restarts.
--
-- Usage:   mysql -u <user> -p < db/dml/03_reset_banking_data.sql
-- =============================================================================

USE db_example;

START TRANSACTION;
DELETE FROM customer_credentials;
DELETE FROM bank_employee_credentials;
DELETE FROM bank_employees;
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
ALTER TABLE bank_employees           AUTO_INCREMENT = 1;
ALTER TABLE bank_employee_credentials AUTO_INCREMENT = 1;
ALTER TABLE customer_credentials     AUTO_INCREMENT = 1;
