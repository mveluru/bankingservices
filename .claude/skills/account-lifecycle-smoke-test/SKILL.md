---
name: account-lifecycle-smoke-test
description: Smoke-test account suspension against the running app (seeded counts, blocked withdraw/deposit, suspend, update, reactivate, close, auto-expiry) using throwaway accounts, then close them (never delete). Writes to the local database, so only run it when the user asks.
disable-model-invocation: true
---

# Account lifecycle smoke test (writes to the local MySQL, then closes its test accounts)

Prereqs: JDK 25 for Maven/the app (`mvn -v`), MySQL up on 3306 (`db_example`), the app running on `http://localhost:8081/brite` (`mvn spring-boot:run`), and `db/ddl/03_account_suspension_migration.sql` applied if the database predates suspension (it is idempotent). Every call needs `X-Customer-Id`. Tell the user before starting that this creates throwaway accounts and leaves them `CLOSED` (rows are never deleted).

```bash
B=http://localhost:8081/brite; H='X-Customer-Id: smoke-1'; J='Content-Type: application/json'
ACC='"street":"1","addressLine1":"1 Main St","city":"Austin","state":"TX","zip":"78701"'
iso() { python3 -c "import datetime,sys;print((datetime.datetime.now()+datetime.timedelta(seconds=float(sys.argv[1]))).strftime('%Y-%m-%dT%H:%M:%S'))" "$1"; }
```

## 1. Seeded data (read-only)
```bash
curl -s -H "$H" "$B/v1/api/accounts?status=SUSPENDED&size=50" | python3 -c "import sys,json;print('suspended',json.load(sys.stdin)['totalElements'])"            # expect 20
curl -s -H "$H" "$B/v1/api/accounts?status=CLOSED&months=48&size=50" | python3 -c "import sys,json;print('closed',json.load(sys.stdin)['totalElements'])"         # expect >= 22
```
Seeded suspended accounts (`CH-0000050001..`, `SV-0000060001..`) must reject `deposit`/`withdraw` with `400` "is suspended"; a rejected call changes nothing, so this part is safe on seed rows. Seeded closed accounts (`CH-0000030001..`) reject with "is closed".

## 2. Lifecycle on a throwaway account (never mutate seed rows)
1. Open one: `POST $B/v1/api/accounts/newaccount` (body in the README) with first name `ClaudeTest` (last name e.g. `Smoke`) so it can't be confused with the portal's accounts, and note its `CH-`/`SV-` number `N`.
2. `deposit` a little money → `200`.
3. `POST $B/v1/api/accounts/N/suspend` `{"notes":"smoke test","endDateTime":"$(iso 172800)"}` → `200`, `SUSPENDED`, `suspended: true`.
4. `withdraw` and `deposit` on `N` → both `400` "is suspended"; balance unchanged.
5. `suspend` again → `400` "already suspended". `PATCH .../N/suspension` with `{"notes":"extended"}` → `200`; with `{"endDateTime":"$(iso 864000)"}` → `200`; with `{}` → `400`; with a past end → `400`.
6. Validations on `suspend`: blank notes → `400`; start in the future → `400`; end before start → `400`; unknown account → `404`.
7. `POST .../N/reactivate` → `200`, `ACTIVE`, suspension fields null; `deposit` → `200`; `reactivate` again → `400` "not suspended".
8. `suspend` (indefinite), then `POST .../N/close` → `CLOSED` with suspension fields cleared; `suspend` on it → `400` "cannot be suspended".

## 3. Auto-expiry (optional, about 1 minute)
Open a second throwaway account, `suspend` it with `endDateTime` = `$(iso 5)`, then poll `GET $B/bff/v1/portal/accounts/N2/overview` every 5 s. It stays `SUSPENDED` (and still rejects transactions) until the job (`banking.suspension.expiry-job.interval-ms`, default 60000) runs, then turns `ACTIVE`; the app log shows "Reactivated 1 account(s) whose suspension ended". The search listing must no longer return it under `status=SUSPENDED` (cache evicted).

## 4. Clean up (required): close, never delete
**Never delete accounts or customers** (no `DELETE`, no reset/drop scripts), including your own test rows. End each throwaway account by closing it, which stamps `closedDate` and leaves the row in place:
```bash
curl -s -X POST -H "$H" "$B/v1/api/accounts/N/close"     # N from step 2 is already closed in step 8: a 400 "already closed" is fine
curl -s -X POST -H "$H" "$B/v1/api/accounts/N2/close"    # the auto-expiry account (reactivated by the job, so ACTIVE): close it
```
Then confirm both show `CLOSED` with a `closedDate` (`GET $B/v1/api/accounts?accountNumber=N&status=CLOSED&months=3`) and list the closed `ClaudeTest` accounts you left in your summary to the user (the account count grows by 2 per run, and `auto_increment`/number reuse is no longer an issue because nothing is deleted). Leave other rows you didn't create alone, even if they look unexpected; mention them instead.

Never use `db/ddl/02_drop_tables.sql` or `db/dml/03_reset_banking_data.sql` (a hook will ask first).
