-- =============================================================================
-- MIGRATION: customer phone number. Safe to run any number of times, before OR after the app has started
-- on the new version (Hibernate's ddl-auto=update may already have added the column).
--
-- Adds customers.phone_number varchar(20) NULL if it is missing. Existing customers keep NULL (the API
-- treats a missing phone as "none on file"); use data/06_backfill_customer_phones.sql to give the demo
-- customers (ids 1..92) their 512-555-NNNN numbers.
--
-- New databases don't need this; ddl/01_create_tables.sql already includes the column.
-- Usage:   mysql -u <user> -p db_example < db/ddl/04_customer_phone_migration.sql
-- =============================================================================

USE db_example;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'customers' AND column_name = 'phone_number') = 0,
              'ALTER TABLE customers ADD COLUMN phone_number varchar(20) NULL', 'SELECT ''phone_number: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
