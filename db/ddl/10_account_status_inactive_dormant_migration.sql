-- =============================================================================
-- MIGRATION: two new account statuses, INACTIVE and DORMANT. Safe to run any number of times (MODIFY COLUMN to the same list is a no-op).
--
-- Hibernate's ddl-auto=update adds columns but never alters an existing enum column, so a database created before this version keeps
-- account_status enum('ACTIVE','CLOSED','SUSPENDED') and writing INACTIVE or DORMANT fails until this runs.
--
-- What the statuses do: neither can withdraw or deposit (400, "contact the customer support service"), and a customer whose accounts are all
-- SUSPENDED, CLOSED, INACTIVE or DORMANT can't sign in (403, same message style). Nothing sets them automatically: use dml/07_account_inactive_dormant.sql.
--
-- New databases don't need this; ddl/01_create_tables.sql already has the full list.
-- Usage:   mysql -u <user> -p db_example < db/ddl/10_account_status_inactive_dormant_migration.sql
-- =============================================================================

USE db_example;

ALTER TABLE accounts MODIFY COLUMN account_status enum ('ACTIVE','CLOSED','DORMANT','INACTIVE','SUSPENDED');
