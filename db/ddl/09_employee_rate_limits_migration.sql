-- =============================================================================
-- MIGRATION: per-employee daily request limit and usage (employee_rate_limits), the staff counterpart of customer_rate_limits. Safe to run
-- any number of times, before OR after the app has started on the new version (Hibernate's ddl-auto=update may already have created the table).
--
-- One row per employee, created by the app on that employee's first token-authenticated request:
--   employee_number       the employee (EMP-000010), a plain value, not a foreign key
--   max_requests_per_day  this employee's own cap per day; NULL = use banking.rate-limit.employee-requests-per-day (default 1000)
--   usage_date            the day request_count / login_count belong to; the first request on a later day starts them from zero
--   request_count         requests counted today (requests carrying that employee's token)
--   login_count           successful employee logins today
--
-- New databases don't need this; ddl/01_create_tables.sql already includes it.
-- Usage:   mysql -u <user> -p db_example < db/ddl/09_employee_rate_limits_migration.sql
-- =============================================================================

USE db_example;

CREATE TABLE IF NOT EXISTS employee_rate_limits (
    login_count integer not null default 0,
    request_count integer not null default 0,
    max_requests_per_day integer,
    usage_date date not null,
    id bigint not null auto_increment,
    employee_number varchar(20) not null,
    primary key (id)
) engine=InnoDB;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'employee_rate_limits' AND index_name = 'idx_employee_rate_limits_employee_number') = 0,
              'ALTER TABLE employee_rate_limits ADD CONSTRAINT idx_employee_rate_limits_employee_number UNIQUE (employee_number)',
              'SELECT ''idx_employee_rate_limits_employee_number: already present''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
