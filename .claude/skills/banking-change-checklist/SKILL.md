---
name: banking-change-checklist
description: Checklist for any change to the banking module (new endpoint, field, status, seed data, exception). Use before and after editing org.brite.banking so code, db/ scripts, OpenAPI, README, rules and tests stay in sync. Trigger on "add an endpoint/field/status to banking", "update banking", or before committing banking changes.
---

# Banking change checklist

Work through the rows that apply, then run the verification commands. The rules behind each row are in `.claude/rules/banking/` (read the matching layer file first).

## 1. Classify the change

| If you changed... | Also update |
|---|---|
| A controller, request, response/domain DTO, validation, exception mapping | `src/main/resources/static/openapi/banking-openapi.yaml`, README endpoint table + curl, `BankingGatewayConfig.BANKING_URL_PATTERNS` (new paths), `BankingExceptionHandler` + a `LOG_HANDLER_*` constant in `BankingMessages` (new exception) |
| An entity column/table/enum, or `AccountDataSeeder` / `AccountStatusDemoSeeder` / `BankLocationDataSeeder` | `db/ddl/01_create_tables.sql`, `db/data/*`, `db/dml/*`, `db/README.md`; **an idempotent `db/ddl/NN_*_migration.sql` for any enum widening or column type change** (Hibernate `ddl-auto: update` adds columns but never alters existing ones, and may already have added them before you run the script) |
| Anything shown in `AccountStatusView` | `@CacheEvict(ACCOUNT_SEARCH_CACHE, allEntries = true)` on the mutation + `AccountSearchCachingTest` |
| Withdraw/deposit or any new transaction type | closed **and suspended** guards before balance checks in `AccountRepository`, plus a test for each |
| A user-visible or log string | a constant in `BankingMessages` (no literals) |
| A BFF screen (`org.brite.banking.bff`) | DTO record, `PortalOrchestrationService`, OpenAPI tag `Portal (BFF)`, README, `PortalControllerTest` / `PortalOrchestrationServiceTest` |
| Behavior described in CLAUDE.md | the matching CLAUDE.md section and rules file, in the same commit |

## 2. Tests (lightest that proves it)
Service = Mockito unit test; controller = standalone MockMvc with the real `BankingExceptionHandler`; `Specification` queries, state rules and seeders = `@DataJpaTest` on H2; filters = `Mock*` servlet objects. Bug fix = regression test first. See `rules/banking/testing.md`.

## 3. Verify
Run Maven on JDK 25 (`mvn -v` must say `Java version: 25.x`; the project targets Java 25).
```bash
mvn -o test -Dtest='*Suspension*Test,AccountRepositoryTest,ClientAccountServiceTest,AccountStatusStatementServiceTest,AccountSearchCachingTest,Portal*Test,BankLocation*Test,LocationBased*Test,*Filter*Test,CustomerRateLimiterTest,AccountStatusDemoSeederTest'
pip install openapi-spec-validator && openapi-spec-validator src/main/resources/static/openapi/banking-openapi.yaml
```
YAML gotchas: quote plain scalars containing `: ` and flow-sequence items containing `,`.

## 4. Before committing
- `git status`: the change should include code + tests + `db/` + OpenAPI + README + the `.claude` docs that apply. Leave unrelated IDE files (`.idea/workspace.xml`) out.
- The PostToolUse hook (`.claude/hooks/banking-sync-reminder.py`) prints the relevant reminders after banking edits; treat them as the short form of this checklist.
- If a live MySQL was touched (migration, seed, smoke test), say exactly what changed.
