-- =============================================================================
-- Accounts that are INACTIVE or DORMANT (hand-run; the app has no endpoint or job that sets these two statuses).
-- Run ddl/10_account_status_inactive_dormant_migration.sql first on an existing database.
--
-- Effect while an account is INACTIVE or DORMANT: it can't withdraw or deposit, and a customer whose accounts are ALL not ACTIVE (suspended, closed,
-- inactive or dormant) can't sign in: they are told their account status and to contact the customer support service. One ACTIVE account is enough to sign in.
-- Nothing is deleted: the account row stays and can be put back to ACTIVE.
-- Usage:   mysql -u <user> -p < db/dml/07_account_inactive_dormant.sql   (edit the account number first)
-- =============================================================================

USE db_example;

-- Mark an account INACTIVE (or DORMANT); a closed account is final and is left alone
UPDATE accounts SET account_status = 'DORMANT'
 WHERE account_number = 'CH-0000010009' AND account_status = 'ACTIVE';

-- Put it back (only from INACTIVE/DORMANT; a suspension has its own reactivate call and a closed account stays closed)
UPDATE accounts SET account_status = 'ACTIVE'
 WHERE account_number = 'CH-0000010009' AND account_status IN ('INACTIVE', 'DORMANT');

-- Customers with a login who can't sign in right now (no ACTIVE account)
SELECT c.id AS customer_id, c.first_name, c.last_name, cc.username,
       GROUP_CONCAT(DISTINCT a.account_status ORDER BY a.account_status) AS account_statuses
  FROM customers c
  JOIN customer_credentials cc ON cc.customer_id = c.id
  JOIN accounts a ON a.customer_id = c.id
 GROUP BY c.id, c.first_name, c.last_name, cc.username
HAVING SUM(a.account_status = 'ACTIVE') = 0;

-- Accounts by status
SELECT account_status, COUNT(*) AS accounts FROM accounts GROUP BY account_status ORDER BY account_status;
