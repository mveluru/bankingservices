# Database scripts

Plain-SQL companions to the JPA entities (MySQL 8, schema `db_example`). The app does not need
any of these - `spring.jpa.hibernate.ddl-auto: update` creates the tables and `AccountDataSeeder`
seeds demo accounts and bank locations on first start. They exist to build/inspect/reset the database by hand.

| Directory | File | What it does |
|---|---|---|
| `ddl/` | `01_create_tables.sql` | `CREATE DATABASE` + all 9 tables, unique index and foreign keys (incl. the account suspension columns) |
| | `02_drop_tables.sql` | Drops all tables (**destructive**) |
| | `04_customer_phone_migration.sql` | Idempotent migration adding `customers.phone_number varchar(20)` if missing (run before or after the app first starts on this version; new databases don't need it) |
| | `05_transaction_handler_migration.sql` | Idempotent migration adding the nullable `account_transactions` columns that record who handled a transaction (`employee_number/name/role`) and where (`bank_location_id/name/type/city/state`); not needed on a fresh database |
| | `06_login_status_migration.sql` | Idempotent migration adding `status` (default `ACTIVE`), `status_reason` and `status_changed_at` to `bank_employee_credentials` and `customer_credentials`; not needed on a fresh database |
| | `03_account_suspension_migration.sql` | **Migration for a database created before account suspension** (idempotent, run before or after the app first starts): adds any missing `suspended`, `suspended_start`, `suspended_end`, `suspension_notes` columns, defaults `suspended` to 0 and widens `account_status` to include `SUSPENDED` (Hibernate's `ddl-auto: update` adds columns but won't alter an existing enum column). Not needed on a fresh database |
| `data/` | `01_seed_customers_accounts.sql` | The 52 demo accounts + customers with phones `512-555-0001..0052` (same as `AccountDataSeeder`) |
| | `02_sample_transactions.sql` | 5 transactions + 2 withdrawal-history rows for the statement endpoint |
| | `04_seed_bank_locations.sql` | The 20 demo bank offices/ATMs + the services each serves (same as `BankLocationDataSeeder`) |
| | `07_seed_bank_employees.sql` | The 21 demo bank employees (3 area managers, 6 managers, 12 tellers; same as `EmployeeDataSeeder`). Run after `04_...`, against an empty `bank_employees` table |
| | `08_seed_employee_credentials.sql` | A login for each of the 21 demo employees (BCrypt hashes only; demo credentials, see the file header). Run after `07_...`, against an empty `bank_employee_credentials` table |
| | `09_seed_customer_credentials.sql` | Demo logins for the first 10 customers (BCrypt hashes only; demo credentials, see the file header). Run against an empty `customer_credentials` table |
| | `05_seed_closed_and_suspended_accounts.sql` | 40 more accounts (ids 53–92): 20 `CLOSED` and 20 `SUSPENDED` (14 with an end date, 6 indefinite, all with notes) — same as `AccountStatusDemoSeeder`. Run after `01_...` |
| | `06_backfill_customer_phones.sql` | Gives the 92 demo customers with no phone their `512-555-NNNN` number, keyed by account (so it stays correct even if customer ids have drifted); only touches NULL phones, safe to rerun. For databases seeded before phone numbers existed |
| `dml/` | `01_account_operations.sql` | Register / withdraw / deposit / close / bulk-close as guarded SQL |
| | `02_queries.sql` | Lookup, paginated search, statement, reporting queries (read-only) |
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
