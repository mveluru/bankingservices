-- =============================================================================
-- MIGRATION: who handled a transaction. Safe to run any number of times, before OR after the app has
-- started on the new version (Hibernate's ddl-auto=update may already have added the columns).
--
-- Adds nullable employee_number, employee_name, employee_role and the branch/ATM snapshot
-- (bank_location_id/name/type/city/state) to account_transactions. Existing rows keep NULL
-- (customer-initiated or recorded before this feature).
--
-- New databases don't need this; ddl/01_create_tables.sql already includes the columns.
-- Usage:   mysql -u <user> -p db_example < db/ddl/05_transaction_handler_migration.sql
-- =============================================================================

USE db_example;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'account_transactions' AND column_name = 'employee_number') = 0,
              'ALTER TABLE account_transactions ADD employee_numberUMN employee_number varchar(20) NULL', 'SELECT ''employee_number: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'account_transactions' AND column_name = 'employee_name') = 0,
              'ALTER TABLE account_transactions ADD employee_nameUMN employee_name varchar(205) NULL', 'SELECT ''employee_name: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'account_transactions' AND column_name = 'employee_role') = 0,
              'ALTER TABLE account_transactions ADD employee_roleUMN employee_role enum(''AREA_MANAGER'',''MANAGER'',''TELLER'') NULL', 'SELECT ''employee_role: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'account_transactions' AND column_name = 'bank_location_id') = 0,
              'ALTER TABLE account_transactions ADD bank_location_idUMN bank_location_id bigint NULL', 'SELECT ''bank_location_id: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'account_transactions' AND column_name = 'bank_location_name') = 0,
              'ALTER TABLE account_transactions ADD bank_location_nameUMN bank_location_name varchar(255) NULL', 'SELECT ''bank_location_name: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'account_transactions' AND column_name = 'bank_location_type') = 0,
              'ALTER TABLE account_transactions ADD bank_location_typeUMN bank_location_type enum(''ATM'',''BOTH'',''OFFICE'') NULL', 'SELECT ''bank_location_type: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'account_transactions' AND column_name = 'bank_location_city') = 0,
              'ALTER TABLE account_transactions ADD bank_location_cityUMN bank_location_city varchar(255) NULL', 'SELECT ''bank_location_city: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'account_transactions' AND column_name = 'bank_location_state') = 0,
              'ALTER TABLE account_transactions ADD bank_location_stateUMN bank_location_state varchar(255) NULL', 'SELECT ''bank_location_state: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
