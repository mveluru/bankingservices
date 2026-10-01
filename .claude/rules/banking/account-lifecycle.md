---
paths:
  - "src/main/java/org/brite/banking/domain/Account*.java"
  - "src/main/java/org/brite/banking/entity/AccountEntity.java"
  - "src/main/java/org/brite/banking/repository/Account*.java"
  - "src/main/java/org/brite/banking/service/AccountSuspension*.java"
  - "src/main/java/org/brite/banking/service/ClientAccountService.java"
  - "src/main/java/org/brite/banking/request/*Suspen*.java"
  - "db/**"
---
# Banking: account lifecycle (ACTIVE / SUSPENDED / CLOSED)

```
            suspend                 reactivate / suspension end passes (expiry job)
  ACTIVE ───────────────▶ SUSPENDED ─────────────────────────────────▶ ACTIVE
    │                        │
    └────────── close ───────┴──────────▶ CLOSED   (final: no reopen, no suspend)
```

| State | withdraw / deposit | suspend | update suspension | reactivate | close |
|---|---|---|---|---|---|
| ACTIVE | allowed | ok | 400 not suspended | 400 not suspended | ok |
| SUSPENDED | **400 `AccountSuspendedException`** | 400 already suspended | ok | ok | ok (clears suspension) |
| CLOSED | 400 `AccountClosedException` | 400 closed | 400 not suspended | 400 not suspended | 400 already closed |

## Invariants
- **A suspended account can't do *any* transaction** (today: withdraw and deposit). Put a new transaction type's guard in `AccountRepository` next to the closed check, *before* balance checks, and add a suspended-rejection test for it. Reads (lookup, statement, search, BFF overview) stay allowed.
- Status and the `suspended` flag are written together, always through the `AccountRepository` facade (`suspend`, `updateSuspension`, `reactivate`, `reactivateExpiredSuspensions`, `closeAccount`). Never set one without the other; `clearSuspension` resets flag/start/end/notes.
- Suspension data is meaningful only while `SUSPENDED`; after reactivate/close it is null (no history). If history is ever needed, add an audit table rather than keeping stale columns.
- Rule placement: time-window validation (start/end vs now) in `AccountSuspensionService`; state-machine checks and "end after *stored* start" in the facade. Messages in `BankingMessages` (`ACCOUNT_SUSPENDED`, `ACCOUNT_ALREADY_SUSPENDED`, `ACCOUNT_NOT_SUSPENDED`, `SUSPENSION_*`).
- `suspendedEnd == null` means indefinite. Only the expiry job (or an explicit reactivate) ends a suspension; it runs `AccountSuspensionService.reactivateExpiredSuspensions()` through the bean proxy so the cache is evicted.
- Every suspension mutation evicts `ACCOUNT_SEARCH_CACHE` (status + suspension fields are in `AccountStatusView`).

## Who may drive the lifecycle
- Customer endpoints are unchanged. The staff endpoints (`/v1/api/staff/accounts/...`) need `SUSPEND_ACCOUNT` / `UPDATE_SUSPENSION` / `REACTIVATE_ACCOUNT` / `CLOSE_ACCOUNT` (managers and up) and an ACTIVE employee with an ACTIVE login; the account state machine above is unchanged and still enforced by the facade. See `employees-and-logins.md`.
- **Suspend, update-suspension and reactivate are staff-only**: there is no customer or portal route for them (they were removed, not just forbidden), so a customer can't lift a suspension a manager applied. Customers can still close their own account.
- Prefer closing over deleting: ending an account is `close` (stamps `closedDate`, keeps the row and the ids that logins and transactions reference).

## Adding a status or a field to the lifecycle
1. Enum value / entity column / domain field / `AccountStatusView` + `toView` / `toDomain`+`toEntity` in the facade.
2. **DB**: `ddl-auto: update` adds columns but never alters an existing enum column: write an **idempotent** `db/ddl/NN_*_migration.sql` (guard `ADD COLUMN` with an `information_schema` check + `PREPARE`, since Hibernate may already have added the columns; `ALTER TABLE ... MODIFY COLUMN account_status enum(...)` is naturally re-runnable), update `db/ddl/01_create_tables.sql` (enum values alphabetical, as Hibernate emits), `db/data`, `db/dml`, `db/README.md`, and apply the migration to any live database before starting the app.
3. Seed data: new seeders are idempotent per account number (see `AccountStatusDemoSeeder`), keep dates relative, keep numbers `CH-`/`SV-` + 10 digits, and mirror them in `db/data/`.
4. `banking-openapi.yaml` (enum, schemas, paths, plain-text error examples), README endpoint table, CLAUDE.md "Account suspension".
5. Tests: facade state rules on H2, service window rules, controller binding/error mapping, seeder counts.
