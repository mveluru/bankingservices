-- =============================================================================
-- DATA: demo logins for the first 10 customers (ids 1..10), as CustomerCredentialSeeder creates them.
--
-- * Run ONLY against an EMPTY customer_credentials table; customers with those ids must exist
--   (ids with no customer are skipped by the join).
-- * DEMO ONLY: username = customer + four-digit customer id, password = 2026 + the same four digits
--   (customer0001 / 20260001). Only the BCrypt hash is stored. Never reuse this scheme for real people.
--
-- Usage:   mysql -u <user> -p < db/data/09_seed_customer_credentials.sql
-- =============================================================================

USE db_example;

START TRANSACTION;

INSERT INTO customer_credentials (customer_id, username, password_hash, failed_attempts, password_changed_at)
SELECT c.id, v.username, v.password_hash, 0, NOW()
  FROM (
    SELECT 1 AS customer_id, 'customer0001' AS username, '$2y$10$XT1M/q72FpXZ2zLr0c42cuS7TPuOEyNdxX4BI7.tlQwIy6o0iLOpi' AS password_hash
    UNION ALL
    SELECT 2 AS customer_id, 'customer0002' AS username, '$2y$10$unu.xbQImbNqzLfqs1ztTejzrN0PddSAK8QcttEd/k59TJQI/DHh2' AS password_hash
    UNION ALL
    SELECT 3 AS customer_id, 'customer0003' AS username, '$2y$10$KDlLD0OwVcsjTD1c8x03fuhaJ/FTuVQ0p9Tkw98pGkVTo2edXHqwa' AS password_hash
    UNION ALL
    SELECT 4 AS customer_id, 'customer0004' AS username, '$2y$10$0lNPad7Ho6Ke48T9j.qepueNwLYmI5s04m30WXbXgBP9845cINY6i' AS password_hash
    UNION ALL
    SELECT 5 AS customer_id, 'customer0005' AS username, '$2y$10$cknF2h.77rluOU8skyCn.e6qPCzv5X2/EfzeXPgMaJXXKBwxJHIpe' AS password_hash
    UNION ALL
    SELECT 6 AS customer_id, 'customer0006' AS username, '$2y$10$NTAFg.k/TVYQTfrqIIkyreQwYCrLLJPOqUbAZnz90O2dyyTb4q2KK' AS password_hash
    UNION ALL
    SELECT 7 AS customer_id, 'customer0007' AS username, '$2y$10$L0gZXTaeyeI/2oUg1C3LsO117WGTKaT9BW0.Pj7qH.fODayF3Q1nG' AS password_hash
    UNION ALL
    SELECT 8 AS customer_id, 'customer0008' AS username, '$2y$10$oU0ndAEJONBWqNA84RsX4e1fmKBdfD9lOQrSDVsodvKM9Pf35gRSW' AS password_hash
    UNION ALL
    SELECT 9 AS customer_id, 'customer0009' AS username, '$2y$10$rvLZy9wjHoSZHOlyiid0wOqjLZNXcwhjlNtHuvLw9H68IpgBc5oYy' AS password_hash
    UNION ALL
    SELECT 10 AS customer_id, 'customer0010' AS username, '$2y$10$nmaBqzTIUZSSdGrLxyfXvOUJVEEq/GTjjg1cJXg6vE1CZpwbBlQZK' AS password_hash
  ) v
  JOIN customers c ON c.id = v.customer_id;

COMMIT;
