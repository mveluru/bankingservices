---
name: account-lifecycle-smoke-test
description: Smoke-test account suspension against the running app (seeded counts, blocked withdraw/deposit, suspend, update, reactivate, close, auto-expiry) using throwaway accounts, then delete the test rows from MySQL. Writes to the local database, so only run it when the user asks.
disable-model-invocation: true
---

# Account lifecycle smoke test (writes to the local MySQL, then cleans up)

Prereqs: MySQL up on 3306 (`db_example`), the app running on `http://localhost:8081/brite` (`mvn spring-boot:run`), and `db/ddl/03_account_suspension_migration.sql` applied if the database predates suspension (it is idempotent). Every call needs `X-Customer-Id`. Tell the user before starting that this creates and then deletes rows.

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
1. Open one: `POST $B/v1/api/accounts/newaccount` (body in the README) and note its `CH-`/`SV-` number `N`.
2. `deposit` a little money → `200`.
3. `POST $B/v1/api/accounts/N/suspend` `{"notes":"smoke test","endDateTime":"$(iso 172800)"}` → `200`, `SUSPENDED`, `suspended: true`.
4. `withdraw` and `deposit` on `N` → both `400` "is suspended"; balance unchanged.
5. `suspend` again → `400` "already suspended". `PATCH .../N/suspension` with `{"notes":"extended"}` → `200`; with `{"endDateTime":"$(iso 864000)"}` → `200`; with `{}` → `400`; with a past end → `400`.
6. Validations on `suspend`: blank notes → `400`; start in the future → `400`; end before start → `400`; unknown account → `404`.
7. `POST .../N/reactivate` → `200`, `ACTIVE`, suspension fields null; `deposit` → `200`; `reactivate` again → `400` "not suspended".
8. `suspend` (indefinite), then `POST .../N/close` → `CLOSED` with suspension fields cleared; `suspend` on it → `400` "cannot be suspended".

## 3. Auto-expiry (optional, about 1 minute)
Open a second throwaway account, `suspend` it with `endDateTime` = `$(iso 5)`, then poll `GET $B/bff/v1/portal/accounts/N2/overview` every 5 s. It stays `SUSPENDED` (and still rejects transactions) until the job (`banking.suspension.expiry-job.interval-ms`, default 60000) runs, then turns `ACTIVE`; the app log shows "Reactivated 1 account(s) whose suspension ended". The search listing must no longer return it under `status=SUSPENDED` (cache evicted).

## 4. Clean up (required)
Show the rows to the user first, then delete by id in foreign-key order. Pass the DB password (see `application.yml`) through `MYSQL_PWD`; don't write it into files. In zsh use a function, not a variable holding the command.
```sql
select a.id, a.account_number, a.customer_id,
       (select count(*) from account_transactions t where t.account_id = a.id) txns,
       (select count(*) from accounts x where x.customer_id = a.customer_id) accts_for_customer
  from accounts a where a.account_number in ('<N>', '<N2>');
start transaction;
delete from account_transactions where account_id in (<ids>);
delete from accounts where id in (<ids>) and account_number in ('<N>', '<N2>');
delete from customers where id in (<customer ids>) and last_name in ('Tester', 'Expiry');
commit;
```
Only delete customers whose `accts_for_customer` is 1. Afterwards confirm the account count is back to what it was (92 with all seed data) and no `Smoke` customers remain. Leave other rows you didn't create alone, even if they look unexpected (for example an account closed by someone else); mention them instead.

Never use `db/ddl/02_drop_tables.sql` or `db/dml/03_reset_banking_data.sql` for cleanup (a hook will ask first). The test advances `auto_increment` counters; that's harmless.
