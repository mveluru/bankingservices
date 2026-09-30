-- =============================================================================
-- DML: the writes the banking module performs, as plain SQL. Each block is a self-contained
-- transaction driven by session variables (@acct, @amount, ...) - edit the SET lines and
-- run one block at a time in a MySQL client session (variables live only for the session).
--
-- The app enforces its business rules in Java (ClientAccountService/AccountRepository) and
-- protects concurrent updates with optimistic locking (accounts.version, bumped on every
-- change). These snippets reproduce the same rules with guarded UPDATEs so you can do the
-- same thing by hand; they are for admin/support use, not something the app runs.
-- ROW_COUNT() after each guarded UPDATE is 1 on success, 0 if a guard rejected it -
-- ROLLBACK instead of continuing when it is 0.
--
-- Rules mirrored (defaults from application.yml `banking.*`):
--   minimum age 18 | checking must keep >= 25.00 after a withdrawal | savings >= 100.00
--   cash deposit <= 5000.00 | CLOSED and SUSPENDED accounts reject withdraw/deposit | no reopen of CLOSED (see 04_account_suspension.sql for suspend/reactivate)
-- =============================================================================

USE db_example;

-- -----------------------------------------------------------------------------
-- 1. Register a new customer + account (POST /v1/api/accounts/newaccount)
--    Account number = CH-/SV- + zero-padded (row count + 10001), same as AccountRepository.save().
--    Age (>= 18) and the 1940+ birth-year rule are NOT checked here - check dateOfBirth yourself.
-- -----------------------------------------------------------------------------
SET @first_name = 'Jane', @last_name = 'Doe', @dob = '1990-03-15';
SET @street = '111', @city = 'Leander', @state = 'TX', @zip = '78717', @line1 = 'Leafvillage', @line2 = 'Unit1';
SET @type = 'CHECKING';                                   -- CHECKING | SAVINGS

START TRANSACTION;
INSERT INTO customers (first_name, last_name, date_of_birth, street, city, state, zip, country, address_line1, address_line2)
VALUES (@first_name, @last_name, @dob, @street, @city, @state, @zip, 'USA', @line1, @line2);
SET @customer_id = LAST_INSERT_ID();

SELECT COUNT(*) + 10001 INTO @next_number FROM accounts;
INSERT INTO accounts (account_number, account_type, account_status, balance, created_date, closed_date, customer_id, version)
VALUES (CONCAT(IF(@type = 'CHECKING', 'CH-', 'SV-'), LPAD(@next_number, 10, '0')),
        @type, 'ACTIVE', 0.00, CURDATE(), NULL, @customer_id, 0);
COMMIT;

-- -----------------------------------------------------------------------------
-- 2. Withdraw (POST /v1/api/accounts/withdraw)
--    The single guarded UPDATE covers: account is ACTIVE, funds are sufficient, and the
--    remaining balance stays at/above the type's minimum (25.00 checking / 100.00 savings).
-- -----------------------------------------------------------------------------
SET @acct = 'CH-0000088291', @amount = 100.75;

START TRANSACTION;
UPDATE accounts
   SET balance = balance - @amount, version = version + 1
 WHERE account_number = @acct
   AND account_status = 'ACTIVE'
   AND balance - @amount >= IF(account_type = 'CHECKING', 25.00, 100.00);
SELECT ROW_COUNT() AS rows_updated;                       -- 0 => rejected: ROLLBACK; instead of continuing

INSERT INTO account_transactions (account_id, transaction_type, amount, balance_after, transaction_date, deposit_type)
SELECT id, 'WITHDRAWAL', @amount, balance, CURDATE(), NULL FROM accounts WHERE account_number = @acct;

INSERT INTO withdrawal_history (account_id, withdrawal_date, amount, status, first_name, last_name,
                                street, city, state, zip, country, address_line1, address_line2)
SELECT a.id, CURDATE(), @amount, 'COMPLETED', c.first_name, c.last_name,
       c.street, c.city, c.state, c.zip, c.country, c.address_line1, c.address_line2
  FROM accounts a JOIN customers c ON c.id = a.customer_id
 WHERE a.account_number = @acct;
COMMIT;

-- -----------------------------------------------------------------------------
-- 3. Deposit (POST /v1/api/accounts/deposit)
--    @deposit_type is 'cash' or 'check'; cash is capped at 5000.00 (checks are not).
-- -----------------------------------------------------------------------------
SET @acct = 'SV-0000044102', @amount = 250.00, @deposit_type = 'check';

START TRANSACTION;
UPDATE accounts
   SET balance = balance + @amount, version = version + 1
 WHERE account_number = @acct
   AND account_status = 'ACTIVE'
   AND (@deposit_type = 'check' OR @amount <= 5000.00);
SELECT ROW_COUNT() AS rows_updated;                       -- 0 => rejected: ROLLBACK; instead of continuing

INSERT INTO account_transactions (account_id, transaction_type, amount, balance_after, transaction_date, deposit_type)
SELECT id, 'DEPOSIT', @amount, balance, CURDATE(), @deposit_type FROM accounts WHERE account_number = @acct;
COMMIT;

-- -----------------------------------------------------------------------------
-- 4. Close one account (POST /v1/api/accounts/{accountNumber}/close)
--    ACTIVE or SUSPENDED -> CLOSED with closed_date = today (a suspension is cleared on close).
--    There is no reopen path in the app. rows_updated = 0 means the account doesn't exist or is already closed.
-- -----------------------------------------------------------------------------
SET @acct = 'CH-0000010001';

UPDATE accounts
   SET account_status = 'CLOSED', closed_date = CURDATE(), version = version + 1,
       suspended = 0, suspended_start = NULL, suspended_end = NULL, suspension_notes = NULL
 WHERE account_number = @acct AND account_status IN ('ACTIVE', 'SUSPENDED');
SELECT ROW_COUNT() AS rows_updated;

-- -----------------------------------------------------------------------------
-- 5. Bulk close (POST /v1/api/accounts/close) - best effort, like the endpoint: accounts
--    that don't exist or are already closed are simply skipped.
-- -----------------------------------------------------------------------------
UPDATE accounts
   SET account_status = 'CLOSED', closed_date = CURDATE(), version = version + 1,
       suspended = 0, suspended_start = NULL, suspended_end = NULL, suspension_notes = NULL
 WHERE account_number IN ('CH-0000010002', 'CH-0000010003', 'SV-0000020001')
   AND account_status IN ('ACTIVE', 'SUSPENDED');
SELECT ROW_COUNT() AS accounts_closed;

-- NOTE: if the app is running, GET /v1/api/accounts results are cached for 10 minutes
-- (Caffeine). Changes made directly in SQL don't evict that cache, so the listing can look
-- stale until it expires or the app is restarted.
