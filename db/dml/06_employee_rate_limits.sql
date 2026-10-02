-- =============================================================================
-- Per-employee daily request limit and usage (employee_rate_limits). Hand-run examples; the app maintains the counters itself.
-- An employee's row appears on their first token-authenticated request. No row, or max_requests_per_day NULL, means the default
-- banking.rate-limit.employee-requests-per-day (1000). Usage counters belong to usage_date; the app starts them again on the first request of a later day.
-- Usage:   mysql -u <user> -p < db/dml/06_employee_rate_limits.sql   (edit the employee numbers first)
-- =============================================================================

USE db_example;

-- Today's usage per employee (requests counted against the token's employee, and successful logins)
SELECT e.employee_number, e.first_name, e.last_name, e.role,
       COALESCE(r.max_requests_per_day, 1000) AS daily_limit, r.request_count, r.login_count, r.usage_date
  FROM employee_rate_limits r JOIN bank_employees e ON e.employee_number = r.employee_number
 WHERE r.usage_date = CURDATE()
 ORDER BY r.request_count DESC;

-- Give one employee their own daily limit (creates the row if the employee has not made a request yet)
INSERT INTO employee_rate_limits (employee_number, max_requests_per_day, usage_date, request_count, login_count)
VALUES ('EMP-000010', 500, CURDATE(), 0, 0)
ON DUPLICATE KEY UPDATE max_requests_per_day = VALUES(max_requests_per_day);

-- Put an employee back on the default limit
UPDATE employee_rate_limits SET max_requests_per_day = NULL WHERE employee_number = 'EMP-000010';

-- Unblock an employee who hit today's limit (zeroes today's request count only)
UPDATE employee_rate_limits SET request_count = 0 WHERE employee_number = 'EMP-000010' AND usage_date = CURDATE();
