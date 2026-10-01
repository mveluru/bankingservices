---
paths:
  - "src/main/java/org/brite/banking/repository/**"
  - "src/main/java/org/brite/banking/entity/**"
  - "db/**"
---
# Banking layer: repositories and persistence

## Two-tier structure
1. `repository/jpa/*JpaRepository`: plain Spring Data interfaces (`JpaRepository`, plus `JpaSpecificationExecutor` when filtering). No logic. Never injected outside `repository/`.
2. `repository/*Repository`: `@Repository @RequiredArgsConstructor` facade classes (not interfaces). They map entity ↔ domain, build `Specification`s, generate account numbers, and enforce data-level rules (closed check, balance check).

## Facade rules
- Public methods take/return domain objects, `Page<Domain>`, or `Optional<Domain>`.
- Reads: `@Transactional(readOnly = true)`. Writes: `@Transactional`.
- Dynamic filters: build one `Specification<XEntity>` by AND-ing predicates for non-null filters. Text filters are case-insensitive equality (`cb.lower`). One query path for both "one" and "many".
- Sorting: validate `Pageable` sort keys against an allow-list **before** calling `findAll`; throw `IllegalArgumentException` with a `BankingMessages.UNSUPPORTED_*_SORT_PROPERTY` message (→ 400). Remap API keys to entity paths (e.g. `city` → `address.city`).
- Accounts: number = `count() + 10001`, `String.format("%010d")`, prefixed `CH-`/`SV-`. New accounts default `ACTIVE`, `createdDate = today`.
- Concurrency: `AccountEntity` uses `@Version`; a concurrent update surfaces `ObjectOptimisticLockingFailureException`. There is no retry; do not swallow it.
- Mapping: `toDomain`/`toEntity` private methods in the facade; null-safe on embeddables.

## Employees, logins and transaction handlers
- Facades: `EmployeeRepository` (read-only, domain `Employee`), `EmployeeCredentialRepository`/`CustomerCredentialRepository` (upsert by owner id, status fields mapped), `CustomerRepository` (id + name only, for login). `AccountRepository.findCustomerIdByAccountNumber` backs the customer-login transaction rule. `TransactionRepository` maps the employee/branch snapshot columns.
- Seeders (`EmployeeDataSeeder`, `EmployeeCredentialSeeder`, `CustomerCredentialSeeder`) are run-once, depend on their base seeder by constructor injection, and use DEMO credentials only (BCrypt-hashed); mirrors `db/data/07`-`09`.
- `SecurityAnswerRepository` (`security_answers`: 3 slots per employee/customer, upsert by slot, no deletes). The credential tables gained `reset_failed_attempts`/`reset_locked_until` (migration `db/ddl/07`).
- New columns on existing tables (`account_transactions` handler columns, login `status`) shipped with idempotent migrations `db/ddl/05`/`06`.

## Seeders
- `@Component` with `@PostConstruct`, seed **only if the table is empty**. Dates are `LocalDate.now().minusMonths(N)`. Seeded numbers/ids are documented in the README/OpenAPI examples; don't renumber.
- Seeder changes → regenerate/update `db/data/*.sql`.

## Schema and `db/`
- `ddl-auto: update` builds the schema, but `db/ddl/01_create_tables.sql` must mirror the entities (keep Hibernate's FK constraint names). Any column/table/type change → update `db/ddl`, `db/data` (if seeded), `db/dml` (if referenced), and `db/README.md`.
- Never edit an entity column type/name without checking existing MySQL data compatibility.
- No native SQL in Java unless a `Specification` cannot express it; if used, bind parameters.

## Suspension and enum columns
- Suspension writes (`suspend`, `updateSuspension`, `reactivate`, `reactivateExpiredSuspensions`, and `closeAccount`'s `clearSuspension`) keep status and the `suspended` flag in step; withdraw/deposit reject when *either* says suspended, after the closed check and before balance checks.
- `AccountJpaRepository.findBySuspendedTrueAndSuspendedEndLessThanEqual(now)` backs expiry; indefinite suspensions (null end) never match.
- **Enum columns are not altered by `ddl-auto: update`.** Widening `account_status` needs `db/ddl/03_account_suspension_migration.sql`-style `MODIFY COLUMN`, applied to live databases.
- Extra demo data goes in its own seeder, idempotent *per account number* (`AccountStatusDemoSeeder`), injecting `AccountDataSeeder` by constructor so the base seed runs first on an empty DB. (`@DependsOn("accountDataSeeder")` breaks under `@Import` test contexts.)
