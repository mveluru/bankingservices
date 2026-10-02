# Database scripts

Plain-SQL companions to the JPA entities (MySQL 8, schema `db_example`). The app does not need
any of these - `spring.jpa.hibernate.ddl-auto: update` creates the tables and `AccountDataSeeder`
seeds demo accounts and bank locations on first start. They exist to build/inspect/reset the database by hand.

| Directory | File | What it does |
|---|---|---|
| `ddl/` | `01_create_tables.sql` | `CREATE DATABASE` + all 12 tables, unique index and foreign keys (incl. the account suspension columns) |
| | `02_drop_tables.sql` | Drops all tables (**destructive**) |
| | `04_customer_phone_migration.sql` | Idempotent migration adding `customers.phone_number varchar(20)` if missing (run before or after the app first starts on this version; new databases don't need it) |
| | `05_transaction_handler_migration.sql` | Idempotent migration adding the nullable `account_transactions` columns that record who handled a transaction (`employee_number/name/role`) and where (`bank_location_id/name/type/city/state`); not needed on a fresh database |
| | `06_login_status_migration.sql` | Idempotent migration adding `status` (default `ACTIVE`), `status_reason` and `status_changed_at` to `bank_employee_credentials` and `customer_credentials`; not needed on a fresh database |
| | `10_account_status_inactive_dormant_migration.sql` | Idempotent `MODIFY COLUMN` widening `accounts.account_status` to `ACTIVE`/`CLOSED`/`DORMANT`/`INACTIVE`/`SUSPENDED` (Hibernate never alters an existing enum column, so an older database needs it before the new statuses can be written) |
| | `09_employee_rate_limits_migration.sql` | Idempotent migration creating `employee_rate_limits` (one row per employee: own `max_requests_per_day`, today's `request_count` and `login_count`); not needed on a fresh database |
| | `08_customer_rate_limits_migration.sql` | Idempotent migration creating `customer_rate_limits` (one row per customer: own `max_requests_per_day`, today's `request_count` and `login_count`); not needed on a fresh database |
| | `07_password_reset_migration.sql` | Idempotent migration for password reset with security questions: adds `reset_failed_attempts` / `reset_locked_until` to both credential tables and creates `security_answers` (three BCrypt-hashed answers per employee/customer); not needed on a fresh database |
| | `03_account_suspension_migration.sql` | **Migration for a database created before account suspension** (idempotent, run before or after the app first starts): adds any missing `suspended`, `suspended_start`, `suspended_end`, `suspension_notes` columns, defaults `suspended` to 0 and widens `account_status` to include `SUSPENDED` (Hibernate's `ddl-auto: update` adds columns but won't alter an existing enum column). Not needed on a fresh database |
| `data/` | `01_seed_customers_accounts.sql` | The 52 demo accounts + customers with phones `512-555-0001..0052` (same as `AccountDataSeeder`) |
| | `02_sample_transactions.sql` | 5 transactions + 2 withdrawal-history rows for the statement endpoint |
| | `04_seed_bank_locations.sql` | The 20 demo bank offices/ATMs + the services each serves (same as `BankLocationDataSeeder`) |
| | `07_seed_bank_employees.sql` | The 21 demo bank employees (3 area managers, 6 managers, 12 tellers; same as `EmployeeDataSeeder`). Run after `04_...`, against an empty `bank_employees` table |
| | `08_seed_employee_credentials.sql` | A login for each of the 21 demo employees (BCrypt hashes only; demo credentials, see the file header). Run after `07_...`, against an empty `bank_employee_credentials` table |
| | `09_seed_customer_credentials.sql` | Demo logins for the first 10 customers (BCrypt hashes only; demo credentials, see the file header). Run against an empty `customer_credentials` table |
| | `05_seed_closed_and_suspended_accounts.sql` | 40 more accounts (ids 53–92): 20 `CLOSED` and 20 `SUSPENDED` (14 with an end date, 6 indefinite, all with notes) — same as `AccountStatusDemoSeeder`. Run after `01_...` |
| | `06_backfill_customer_phones.sql` | Gives the 92 demo customers with no phone their `512-555-NNNN` number, keyed by account (so it stays correct even if customer ids have drifted); only touches NULL phones, safe to rerun. For databases seeded before phone numbers existed |
| | `11_backup_employee_rate_limits.sql` | Snapshot (data backup) of `employee_rate_limits` (employee numbers with their own `max_requests_per_day`, NULL = the property default of 1000) and that day's counters; re-runnable (`ON DUPLICATE KEY UPDATE`); a fresh database needs none |
| | `10_backup_customer_rate_limits.sql` | Snapshot (data backup) of `customer_rate_limits` (customers 1-4, `max_requests_per_day` NULL = the property default of 1000) and that day's counters; re-runnable (`ON DUPLICATE KEY UPDATE`); a fresh database needs none |
| `dml/` | `01_account_operations.sql` | Register / withdraw / deposit / close / bulk-close as guarded SQL |
| | `02_queries.sql` | Lookup, paginated search, statement, reporting queries (read-only) |
| | `07_account_inactive_dormant.sql` | Mark an account INACTIVE/DORMANT or put it back, list the customers who can't sign in because no account is ACTIVE, count accounts by status |
| | `06_employee_rate_limits.sql` | Today's request/login usage per employee, and how to give an employee their own daily limit, put them back on the default or unblock them |
| | `05_customer_rate_limits.sql` | Today's request/login usage per customer, and how to give a customer their own daily limit, put them back on the default or unblock them |
| | `04_account_suspension.sql` | Suspend / update / reactivate / expire-finished-suspensions as guarded SQL, plus suspended-account reporting |
| | `03_reset_banking_data.sql` | Deletes all banking rows, **including employees and both login tables** (logins reference customers/employees by id, and ids restart at 1), restarts ids (**destructive**; guarded by the db-destructive-guard hook) |

Run in order, e.g. `mysql -u <user> -p < db/ddl/01_create_tables.sql`, then the `data/` files.

- `01_create_tables.sql` is generated from the entities; if you change an entity, regenerate it
  (and update `data/` + `dml/` for any column change) so the scripts don't drift from the app.
- `data/04_...` must run against empty `bank_locations`/`bank_location_services` tables.
- `data/07_...` needs the locations from `data/04_...` first and an empty `bank_employees` table.
- `data/01_...` must run against empty `accounts`/`customers` tables. Its dates are relative to
  `CURDATE()`, like the seeder's `LocalDate.now()`.
- `data/05_...` runs after `data/01_...` and expects ids 53–92 free. `AccountStatusDemoSeeder` seeds the same 40 accounts on app start for any that are missing (per account number), so an existing database gets them without running this file.
- SQL changes bypass the app's 10-minute `GET /v1/api/accounts` cache, so listings can look stale.

## Adding a table (do all of these in the same change)
Whenever a new `*Entity` (a new table) is added, the `db/` folder gets every one of these; `doc-sync.py` blocks the end of a turn that adds an entity without them:
1. **DDL**: the table in `ddl/01_create_tables.sql` (as Hibernate generates it, with its unique indexes), a `DROP TABLE IF EXISTS` in `ddl/02_drop_tables.sql`, and an idempotent `ddl/NN_<name>_migration.sql` (`CREATE TABLE IF NOT EXISTS` + guarded index) for databases that already exist.
2. **DML**: `dml/NN_<name>.sql` with the useful hand-run statements (usage/reporting queries, how to change the data safely), and a `DELETE` plus `AUTO_INCREMENT = 1` for the table in `dml/03_reset_banking_data.sql`.
3. **Data**: `data/NN_<name>.sql`: a **backup** (snapshot) of the table's current rows as re-runnable `INSERT ... ON DUPLICATE KEY UPDATE`, or, when the app seeds the table itself, a mirror of that seeder. Take the snapshot from the live database (rows with credentials stay hashes only).
4. List all of them in the tables above, and update the table counts in this file.

