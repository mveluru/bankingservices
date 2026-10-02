-- =============================================================================
-- DATA BACKUP: employee_rate_limits as it stood on 2026-10-01 (3 rows: per-employee daily request limit and that day's usage).
--
-- * A snapshot, not a seed: the app creates an employee's row on their first token-authenticated request, so a fresh database needs no data
--   here. Restore it to bring back employees' own limits (max_requests_per_day; NULL = banking.rate-limit.employee-requests-per-day, 1000)
--   and that day's counters.
-- * Safe to re-run: existing rows (unique employee_number) are overwritten with the snapshot values.
-- * Re-take the backup whenever the table's contents matter (see "Adding a table" in db/README.md).
--
-- Usage:   mysql -u <user> -p < db/data/11_backup_employee_rate_limits.sql
-- =============================================================================

USE db_example;

INSERT INTO employee_rate_limits (employee_number, max_requests_per_day, usage_date, request_count, login_count) VALUES
    ('EMP-000001', NULL, '2026-10-01', 5, 1),
    ('EMP-000004', NULL, '2026-10-01', 1, 2),
    ('EMP-000010', NULL, '2026-10-01', 0, 1)
ON DUPLICATE KEY UPDATE max_requests_per_day = VALUES(max_requests_per_day), usage_date = VALUES(usage_date),
                        request_count = VALUES(request_count), login_count = VALUES(login_count);
