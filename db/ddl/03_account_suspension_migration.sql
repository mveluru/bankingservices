-- =============================================================================
-- MIGRATION: account suspension. Safe to run any number of times, before OR after the app has
-- started on the new version.
--
-- Why it is needed even though the app uses spring.jpa.hibernate.ddl-auto=update: on its first start
-- Hibernate adds the missing columns itself (with no DEFAULT), but it never alters an EXISTING column's
-- type, so accounts.account_status keeps enum('ACTIVE','CLOSED') and any write of 'SUSPENDED' fails
-- ("Data truncated for column 'account_status'"). Until this runs, AccountStatusDemoSeeder also stops
-- at its first suspended account (the closed ones before it are kept; a later start finishes the rest).
--
-- What it does (each step only if needed):
--   1. adds suspended / suspended_start / suspended_end / suspension_notes if missing
--   2. gives suspended DEFAULT b'0' (existing rows are "not suspended"; hand-run INSERTs may omit it)
--   3. widens account_status to enum('ACTIVE','CLOSED','SUSPENDED')
--
-- New databases don't need this; ddl/01_create_tables.sql already includes it.
-- Usage:   mysql -u <user> -p db_example < db/ddl/03_account_suspension_migration.sql
-- =============================================================================

USE db_example;

-- 1. add each column only if it isn't there yet (MySQL 8 has no ADD COLUMN IF NOT EXISTS)
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'accounts' AND column_name = 'suspended') = 0,
              'ALTER TABLE accounts ADD COLUMN suspended bit not null default b''0''', 'SELECT ''suspended: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'accounts' AND column_name = 'suspended_start') = 0,
              'ALTER TABLE accounts ADD COLUMN suspended_start datetime(6) NULL', 'SELECT ''suspended_start: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'accounts' AND column_name = 'suspended_end') = 0,
              'ALTER TABLE accounts ADD COLUMN suspended_end datetime(6) NULL', 'SELECT ''suspended_end: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'accounts' AND column_name = 'suspension_notes') = 0,
              'ALTER TABLE accounts ADD COLUMN suspension_notes varchar(500) NULL', 'SELECT ''suspension_notes: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. default for a column Hibernate may have created without one (no-op if it's already there)
ALTER TABLE accounts ALTER COLUMN suspended SET DEFAULT b'0';

-- 3. widen the enum (a no-op when it already includes SUSPENDED)
ALTER TABLE accounts MODIFY COLUMN account_status enum ('ACTIVE','CLOSED','SUSPENDED');
