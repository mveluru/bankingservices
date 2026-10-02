-- =============================================================================
-- MIGRATION: password reset with security questions. Safe to run any number of times, before OR after the app has
-- started on the new version (Hibernate's ddl-auto=update may already have created the table / added the columns).
--
-- * bank_employee_credentials and customer_credentials get reset_failed_attempts (default 0) and reset_locked_until:
--   wrong security answers given to the password reset, and the lock they cause (banking.password-reset.*).
-- * security_answers (new): three answers per employee or customer, one row per slot 1..3, BCrypt hashes only.
--
-- New databases don't need this; ddl/01_create_tables.sql already includes everything.
-- Usage:   mysql -u <user> -p db_example < db/ddl/07_password_reset_migration.sql
-- =============================================================================

USE db_example;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'bank_employee_credentials' AND column_name = 'reset_failed_attempts') = 0,
              'ALTER TABLE bank_employee_credentials ADD COLUMN reset_failed_attempts int NOT NULL DEFAULT 0', 'SELECT ''bank_employee_credentials.reset_failed_attempts: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'bank_employee_credentials' AND column_name = 'reset_locked_until') = 0,
              'ALTER TABLE bank_employee_credentials ADD COLUMN reset_locked_until datetime(6) NULL', 'SELECT ''bank_employee_credentials.reset_locked_until: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'customer_credentials' AND column_name = 'reset_failed_attempts') = 0,
              'ALTER TABLE customer_credentials ADD COLUMN reset_failed_attempts int NOT NULL DEFAULT 0', 'SELECT ''customer_credentials.reset_failed_attempts: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'customer_credentials' AND column_name = 'reset_locked_until') = 0,
              'ALTER TABLE customer_credentials ADD COLUMN reset_locked_until datetime(6) NULL', 'SELECT ''customer_credentials.reset_locked_until: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS security_answers (
    slot integer not null,
    owner_id bigint not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    answer_hash varchar(100) not null,
    owner_type enum ('CUSTOMER','EMPLOYEE') not null,
    question enum ('BIRTH_CITY','CHILDHOOD_FRIEND','FIRST_CAR','FIRST_PET','FIRST_SCHOOL','FIRST_TEACHER') not null,
    primary key (id),
    constraint idx_security_answers_owner_slot unique (owner_type, owner_id, slot)
) engine=InnoDB;
