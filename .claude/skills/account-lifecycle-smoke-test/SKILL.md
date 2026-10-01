---
name: account-lifecycle-smoke-test
description: Smoke-test account suspension against the running app (blocked withdraw/deposit, suspend, update, reactivate, close, auto-expiry) with a manager's employee token and throwaway accounts, then close them (never delete). Writes to the local database, so only run it when the user asks.
disable-model-invocation: true
---

# Account lifecycle smoke test (writes to the local MySQL, then closes its test accounts)

Suspend, update-suspension and reactivate are **staff-only**, so this test acts as a manager through the staff endpoints with an employee token (`/v1/api/staff/accounts/...`); customers have no route for them. Registration (`newaccount`) needs no token.

Prereqs: JDK 25 for Maven/the app (`mvn -v`), MySQL up on 3306 (`db_example`), the app running on `http://localhost:8081/brite` (`mvn spring-boot:run`) **with `BANKING_JWT_SECRET` set** (without it the signing key changes on every restart, including DevTools restarts, and tokens stop working), and `db/ddl/03_account_suspension_migration.sql` applied if the database predates suspension (it is idempotent). Every call needs `X-Customer-Id` (a rate-limit key). Tell the user before starting that this creates throwaway accounts and leaves them `CLOSED` (rows are never deleted).

```bash
B=http://localhost:8081/brite; H='X-Customer-Id: smoke-1'; J='Content-Type: application/json'
ACC='"street":"1","addressLine1":"1 Main St","city":"Austin","state":"TX","zip":"78701"'
iso() { python3 -c "import datetime,sys;print((datetime.datetime.now()+datetime.timedelta(seconds=float(sys.argv[1]))).strftime('%Y-%m-%dT%H:%M:%S'))" "$1"; }
# demo manager marcus.bell (password 2026 + 4-digit employee number): suspend/update/reactivate/close need MANAGER or above
MGR=$(curl -s -X POST -H "$H" -H "$J" $B/v1/api/staff/login -d '{"username":"marcus.bell","password":"20260004"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
A="Authorization: Bearer $MGR"
```
A teller's token (`lucas.meyer` / `20260010`) must get `403` on suspend, update, reactivate and close; check that once.

## 1. Seeded data (safe on seed rows: a rejected call changes nothing)
Seeded suspended accounts (`CH-0000050001..`, `SV-0000060001..`) must reject a staff `deposit`/`withdraw` with `400` "is suspended"; seeded closed accounts (`CH-0000030001..`) reject with "is closed". The global counts (20 suspended, >= 22 closed) can't be read through the API any more: the account list is scoped to the logged-in customer, so check them with SQL instead if needed.

## 2. Lifecycle on a throwaway account (never mutate seed rows)
1. Open one: `POST $B/v1/api/accounts/newaccount` (body in the README) with first name `ClaudeTest` (last name e.g. `Smoke`) so it can't be confused with the portal's accounts, and note its `CH-`/`SV-` number `N`.
2. Staff `deposit` a little money (`POST $B/v1/api/staff/accounts/deposit`, body like the customer one) → `200`.
3. `POST $B/v1/api/staff/accounts/N/suspend` `{"notes":"smoke test","endDateTime":"$(iso 172800)"}` → `200`, `SUSPENDED`, `suspended: true`.
4. Staff `withdraw` and `deposit` on `N` → both `400` "is suspended"; balance unchanged.
5. `suspend` again → `400` "already suspended". `PATCH .../staff/accounts/N/suspension` with `{"notes":"extended"}` → `200`; with `{"endDateTime":"$(iso 864000)"}` → `200`; with `{}` → `400`; with a past end → `400`.
6. Validations on `suspend`: blank notes → `400`; start in the future → `400`; end before start → `400`; unknown account → `404`.
7. `POST .../staff/accounts/N/reactivate` → `200`, `ACTIVE`, suspension fields null; staff `deposit` → `200`; `reactivate` again → `400` "not suspended".
8. `suspend` (indefinite), then `POST .../staff/accounts/N/close` → `CLOSED` with suspension fields cleared; `suspend` on it → `400` "cannot be suspended".
9. Customers can't do 3, 5 or 7: `POST $B/v1/api/accounts/N/suspend` and `.../reactivate`, `PATCH .../suspension`, and the portal equivalents answer `404` (no such routes, whatever the token).

## 3. Auto-expiry (optional, about 1 minute)
Open a second throwaway account, staff `suspend` it with `endDateTime` = `$(iso 5)`, then probe with a staff `deposit` of 1.00 every 5 s. It answers `400` "is suspended" until the job (`banking.suspension.expiry-job.interval-ms`, default 60000) runs, then `200` (the account is `ACTIVE` again); the app log shows "Reactivated 1 account(s) whose suspension ended".

## 4. Clean up (required): close, never delete
**Never delete accounts or customers** (no `DELETE`, no reset/drop scripts), including your own test rows. End each throwaway account by closing it, which stamps `closedDate` and leaves the row in place:
```bash
curl -s -X POST -H "$H" -H "$A" "$B/v1/api/staff/accounts/N/close"     # N from step 2 is already closed in step 8: a 400 "already closed" is fine
curl -s -X POST -H "$H" -H "$A" "$B/v1/api/staff/accounts/N2/close"    # the auto-expiry account (reactivated by the job, so ACTIVE): close it
```
Each close returns the `Account` with `accountStatus: CLOSED` and a `closedDate`. List the closed `ClaudeTest` accounts you left in your summary to the user (the account count grows by 2 per run, and `auto_increment`/number reuse is no longer an issue because nothing is deleted). Leave other rows you didn't create alone, even if they look unexpected; mention them instead.

Never use `db/ddl/02_drop_tables.sql` or `db/dml/03_reset_banking_data.sql` (a hook will ask first).
