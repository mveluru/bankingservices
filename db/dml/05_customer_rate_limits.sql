-- =============================================================================
-- Per-customer daily request limit and usage (customer_rate_limits). Hand-run examples; the app maintains the counters itself.
-- A customer's row appears on their first token-authenticated request. No row, or max_requests_per_day NULL, means the default
-- banking.rate-limit.requests-per-day (1000). Usage counters belong to usage_date; the app starts them again on the first request of a later day.
-- Usage:   mysql -u <user> -p < db/dml/05_customer_rate_limits.sql   (edit the ids first)
-- =============================================================================

USE db_example;

-- Today's usage per customer (requests counted against the token's customer, and successful logins)
SELECT c.id AS customer_id, c.first_name, c.last_name,
       COALESCE(r.max_requests_per_day, 1000) AS daily_limit, r.request_count, r.login_count, r.usage_date
  FROM customer_rate_limits r JOIN customers c ON c.id = r.customer_id
 WHERE r.usage_date = CURDATE()
 ORDER BY r.request_count DESC;

-- Give one customer their own daily limit (creates the row if the customer has not made a request yet)
INSERT INTO customer_rate_limits (customer_id, max_requests_per_day, usage_date, request_count, login_count)
VALUES (3, 250, CURDATE(), 0, 0)
ON DUPLICATE KEY UPDATE max_requests_per_day = VALUES(max_requests_per_day);

-- Put a customer back on the default limit
UPDATE customer_rate_limits SET max_requests_per_day = NULL WHERE customer_id = 3;

-- Unblock a customer who hit today's limit (zeroes today's request count only)
UPDATE customer_rate_limits SET request_count = 0 WHERE customer_id = 3 AND usage_date = CURDATE();
