-- =============================================================================
-- MIGRATION: per-customer daily request limit and usage (customer_rate_limits). Safe to run any number of times, before OR after
-- the app has started on the new version (Hibernate's ddl-auto=update may already have created the table).
--
-- One row per customer, created by the app on that customer's first token-authenticated request:
--   max_requests_per_day  this customer's own cap per day; NULL = use banking.rate-limit.requests-per-day (default 1000)
--   usage_date            the day request_count / login_count belong to; the first request on a later day starts them from zero
--   request_count         requests counted today (requests carrying that customer's token)
--   login_count           successful customer logins today
--
-- New databases don't need this; ddl/01_create_tables.sql already includes it.
-- Usage:   mysql -u <user> -p db_example < db/ddl/08_customer_rate_limits_migration.sql
-- =============================================================================

USE db_example;

CREATE TABLE IF NOT EXISTS customer_rate_limits (
    login_count integer not null default 0,
    request_count integer not null default 0,
    max_requests_per_day integer,
    usage_date date not null,
    customer_id bigint not null,
    id bigint not null auto_increment,
    primary key (id)
) engine=InnoDB;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'customer_rate_limits' AND index_name = 'idx_customer_rate_limits_customer_id') = 0,
              'ALTER TABLE customer_rate_limits ADD CONSTRAINT idx_customer_rate_limits_customer_id UNIQUE (customer_id)',
              'SELECT ''idx_customer_rate_limits_customer_id: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
