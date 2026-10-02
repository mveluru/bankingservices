-- =============================================================================
-- DATA BACKUP: customer_rate_limits as it stood on 2026-10-01 (4 rows: per-customer daily request limit and that day's usage).
--
-- * A snapshot, not a seed: the app creates a customer's row on their first token-authenticated request, so a fresh database needs no
--   data here. Restore it to bring back customers' own limits (max_requests_per_day; NULL = the default) and that day's counters.
-- * Safe to re-run: existing rows (unique customer_id) are overwritten with the snapshot values.
-- * Re-take the backup whenever the table's contents matter (see "Adding a table" in db/README.md).
--
-- Usage:   mysql -u <user> -p < db/data/10_backup_customer_rate_limits.sql
-- =============================================================================

USE db_example;

INSERT INTO customer_rate_limits (customer_id, max_requests_per_day, usage_date, request_count, login_count) VALUES
    (1, NULL, '2026-10-01', 120, 28),
    (2, NULL, '2026-10-01', 15, 4),
    (3, NULL, '2026-10-01', 0, 2),
    (4, NULL, '2026-10-01', 1, 1)
ON DUPLICATE KEY UPDATE max_requests_per_day = VALUES(max_requests_per_day), usage_date = VALUES(usage_date),
                        request_count = VALUES(request_count), login_count = VALUES(login_count);
