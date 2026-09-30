-- =============================================================================
-- DML: read queries equivalent to what the banking endpoints run. Edit the SET lines and
-- run in a MySQL client session. Read-only - safe against a live database.
-- =============================================================================

USE db_example;

-- -----------------------------------------------------------------------------
-- 1. Account + customer profile (POST /v1/api/accounts/lookup)
-- -----------------------------------------------------------------------------
SET @acct = 'CH-0000088291';
SELECT a.account_number, a.account_type, a.account_status, a.balance, a.created_date, a.closed_date,
       c.first_name, c.last_name, c.date_of_birth,
       c.street, c.city, c.state, c.zip, c.country, c.address_line1, c.address_line2
  FROM accounts a
  JOIN customers c ON c.id = a.customer_id
 WHERE a.account_number = @acct;

-- -----------------------------------------------------------------------------
-- 2. Account search (GET /v1/api/accounts) - AccountRepository.search()
--    Every filter is optional (NULL = ignore it). If both created bounds are NULL the created
--    range defaults to the last @months months (18 if @months is NULL) - supplying either
--    bound switches that default off, exactly like the endpoint. Sortable columns are
--    created_date, closed_date, account_status, account_number. Balance is never exposed.
-- -----------------------------------------------------------------------------
SET @acct = NULL, @status = NULL;                         -- @status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED'
SET @created_from = NULL, @created_to = NULL, @closed_from = NULL, @closed_to = NULL;
SET @months = NULL;
SET @page = 0, @size = 20;

SET @from = IF(@created_from IS NULL AND @created_to IS NULL,
               DATE_SUB(CURDATE(), INTERVAL COALESCE(@months, 18) MONTH), @created_from);
SET @to   = IF(@created_from IS NULL AND @created_to IS NULL, CURDATE(), @created_to);

SELECT a.account_number, a.account_type, a.account_status, a.created_date, a.closed_date,
       c.first_name, c.last_name
  FROM accounts a
  JOIN customers c ON c.id = a.customer_id
 WHERE (@acct   IS NULL OR LOWER(a.account_number) = LOWER(@acct))
   AND (@status IS NULL OR a.account_status = @status)
   AND (@from   IS NULL OR a.created_date >= @from)
   AND (@to     IS NULL OR a.created_date <= @to)
   AND (@closed_from IS NULL OR a.closed_date >= @closed_from)
   AND (@closed_to   IS NULL OR a.closed_date <= @closed_to)
 ORDER BY a.created_date ASC
 LIMIT 20 OFFSET 0;                                       -- = @size OFFSET @page * @size

-- Total for the same filters (Page.totalElements): rerun the query above with COUNT(*) in place
-- of the column list and without ORDER BY/LIMIT.

-- -----------------------------------------------------------------------------
-- 3. Bank statement (GET /v1/api/accounts/{accountNumber}/statement) - both dates inclusive.
--    The app additionally rejects end < begin and ranges over 18 months (banking.constraints).
-- -----------------------------------------------------------------------------
SET @acct = 'CH-0000088291', @begin = DATE_SUB(CURDATE(), INTERVAL 3 MONTH), @end = CURDATE();
SELECT a.account_number, t.transaction_type, t.amount, t.balance_after, t.transaction_date, t.deposit_type
  FROM account_transactions t
  JOIN accounts a ON a.id = t.account_id
 WHERE a.account_number = @acct
   AND t.transaction_date BETWEEN @begin AND @end
 ORDER BY t.transaction_date, t.id;

-- -----------------------------------------------------------------------------
-- 4. Reporting helpers
-- -----------------------------------------------------------------------------
-- Accounts and total balance by type and status
SELECT account_type, account_status, COUNT(*) AS accounts, SUM(balance) AS total_balance
  FROM accounts
 GROUP BY account_type, account_status
 ORDER BY account_type, account_status;

-- Ledger integrity: each account's latest balance_after should equal its current balance
-- (rows returned = accounts whose ledger and balance disagree; expect none for an app-only DB)
SELECT a.account_number, a.balance, last_tx.balance_after
  FROM accounts a
  JOIN account_transactions last_tx
    ON last_tx.id = (SELECT MAX(t.id) FROM account_transactions t WHERE t.account_id = a.id)
 WHERE a.balance <> last_tx.balance_after;

-- Withdrawal history for one account
SELECT w.withdrawal_date, w.amount, w.status, w.first_name, w.last_name
  FROM withdrawal_history w
  JOIN accounts a ON a.id = w.account_id
 WHERE a.account_number = 'CH-0000088291'
 ORDER BY w.withdrawal_date;
