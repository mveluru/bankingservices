-- =============================================================================
-- DML: account suspension as guarded SQL, mirroring the endpoints (AccountSuspensionService +
-- AccountRepository). A SUSPENDED account rejects every withdraw/deposit until it is ACTIVE again.
-- Not executed by the app. Changes made in SQL don't evict the 10-minute GET /v1/api/accounts cache.
-- =============================================================================

USE db_example;

-- -----------------------------------------------------------------------------
-- 1. Suspend (POST /v1/api/staff/accounts/{accountNumber}/suspend, staff only)
--    ACTIVE -> SUSPENDED. @start defaults to now (must not be in the future); @end NULL = indefinite,
--    otherwise it must be after @start and in the future. rows_updated = 0 => account missing,
--    CLOSED, or already SUSPENDED (ROLLBACK / stop).
-- -----------------------------------------------------------------------------
SET @acct = 'CH-0000010001', @start = NOW(6), @end = DATE_ADD(NOW(6), INTERVAL 14 DAY),
    @notes = 'Suspected fraudulent card activity - under review';

UPDATE accounts
   SET account_status = 'SUSPENDED', suspended = 1, suspended_start = @start, suspended_end = @end,
       suspension_notes = @notes, version = version + 1
 WHERE account_number = @acct AND account_status = 'ACTIVE'
   AND @start <= NOW(6) AND (@end IS NULL OR (@end > @start AND @end > NOW(6)));
SELECT ROW_COUNT() AS rows_updated;

-- -----------------------------------------------------------------------------
-- 2. Update the suspension (PATCH /v1/api/staff/accounts/{accountNumber}/suspension, staff only)
--    Only supplied values change (NULL = keep). The new end must be after the stored start and in the
--    future. rows_updated = 0 => not suspended, or the new end is invalid.
-- -----------------------------------------------------------------------------
SET @acct = 'CH-0000010001', @new_end = DATE_ADD(NOW(6), INTERVAL 30 DAY), @new_notes = NULL;

UPDATE accounts
   SET suspended_end = COALESCE(@new_end, suspended_end),
       suspension_notes = COALESCE(@new_notes, suspension_notes),
       version = version + 1
 WHERE account_number = @acct AND account_status = 'SUSPENDED'
   AND (@new_end IS NULL OR (@new_end > suspended_start AND @new_end > NOW(6)));
SELECT ROW_COUNT() AS rows_updated;

-- -----------------------------------------------------------------------------
-- 3. Reactivate (POST /v1/api/staff/accounts/{accountNumber}/reactivate, staff only)
--    SUSPENDED -> ACTIVE and the suspension fields are cleared. rows_updated = 0 => not suspended.
-- -----------------------------------------------------------------------------
SET @acct = 'CH-0000010001';

UPDATE accounts
   SET account_status = 'ACTIVE', suspended = 0, suspended_start = NULL, suspended_end = NULL,
       suspension_notes = NULL, version = version + 1
 WHERE account_number = @acct AND account_status = 'SUSPENDED';
SELECT ROW_COUNT() AS rows_updated;

-- -----------------------------------------------------------------------------
-- 4. Expire finished suspensions (what AccountSuspensionExpiryJob does every 60s).
--    Indefinite suspensions (suspended_end IS NULL) never match.
-- -----------------------------------------------------------------------------
UPDATE accounts
   SET account_status = 'ACTIVE', suspended = 0, suspended_start = NULL, suspended_end = NULL,
       suspension_notes = NULL, version = version + 1
 WHERE suspended = 1 AND suspended_end IS NOT NULL AND suspended_end <= NOW(6);
SELECT ROW_COUNT() AS suspensions_expired;

-- -----------------------------------------------------------------------------
-- 5. Reporting
-- -----------------------------------------------------------------------------
-- Currently suspended accounts, soonest-ending first (indefinite last)
SELECT a.account_number, c.first_name, c.last_name, a.suspended_start, a.suspended_end, a.suspension_notes
  FROM accounts a JOIN customers c ON c.id = a.customer_id
 WHERE a.account_status = 'SUSPENDED'
 ORDER BY a.suspended_end IS NULL, a.suspended_end;

-- Accounts by status
SELECT account_status, COUNT(*) AS accounts FROM accounts GROUP BY account_status;
