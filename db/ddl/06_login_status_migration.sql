-- =============================================================================
-- MIGRATION: login status (ACTIVE / INACTIVE / LOCKED / SUSPENDED) on bank_employee_credentials and
-- customer_credentials. Safe to run any number of times, before OR after the app has started on the
-- new version (Hibernate's ddl-auto=update may already have added the columns).
--
-- Existing logins become ACTIVE (the column default). Only an ACTIVE login may perform transactions.
-- New databases don't need this; ddl/01_create_tables.sql already includes the columns.
-- Usage:   mysql -u <user> -p db_example < db/ddl/06_login_status_migration.sql
-- =============================================================================

USE db_example;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'bank_employee_credentials' AND column_name = 'status') = 0,
              'ALTER TABLE bank_employee_credentials ADD statusUMN status enum(''ACTIVE'',''INACTIVE'',''LOCKED'',''SUSPENDED'') NOT NULL DEFAULT ''ACTIVE''', 'SELECT ''bank_employee_credentials.status: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'bank_employee_credentials' AND column_name = 'status_reason') = 0,
              'ALTER TABLE bank_employee_credentials ADD status_reasonUMN status_reason varchar(200) NULL', 'SELECT ''bank_employee_credentials.status_reason: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'bank_employee_credentials' AND column_name = 'status_changed_at') = 0,
              'ALTER TABLE bank_employee_credentials ADD status_changed_atUMN status_changed_at datetime(6) NULL', 'SELECT ''bank_employee_credentials.status_changed_at: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'customer_credentials' AND column_name = 'status') = 0,
              'ALTER TABLE customer_credentials ADD statusUMN status enum(''ACTIVE'',''INACTIVE'',''LOCKED'',''SUSPENDED'') NOT NULL DEFAULT ''ACTIVE''', 'SELECT ''customer_credentials.status: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'customer_credentials' AND column_name = 'status_reason') = 0,
              'ALTER TABLE customer_credentials ADD status_reasonUMN status_reason varchar(200) NULL', 'SELECT ''customer_credentials.status_reason: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'customer_credentials' AND column_name = 'status_changed_at') = 0,
              'ALTER TABLE customer_credentials ADD status_changed_atUMN status_changed_at datetime(6) NULL', 'SELECT ''customer_credentials.status_changed_at: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
