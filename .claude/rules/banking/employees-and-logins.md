---
paths:
  - "src/main/java/org/brite/banking/domain/Employee*.java"
  - "src/main/java/org/brite/banking/domain/Login*.java"
  - "src/main/java/org/brite/banking/domain/*Credential*.java"
  - "src/main/java/org/brite/banking/domain/AuthenticatedCustomer.java"
  - "src/main/java/org/brite/banking/domain/TransactionHandler.java"
  - "src/main/java/org/brite/banking/entity/Employee*.java"
  - "src/main/java/org/brite/banking/entity/*Credential*.java"
  - "src/main/java/org/brite/banking/repository/Employee*.java"
  - "src/main/java/org/brite/banking/repository/*Credential*.java"
  - "src/main/java/org/brite/banking/repository/CustomerRepository.java"
  - "src/main/java/org/brite/banking/service/Employee*.java"
  - "src/main/java/org/brite/banking/service/Staff*.java"
  - "src/main/java/org/brite/banking/service/LoginSupport.java"
  - "src/main/java/org/brite/banking/service/CustomerCredentialService.java"
  - "src/main/java/org/brite/banking/contoller/StaffController.java"
  - "src/main/java/org/brite/banking/contoller/LoginController.java"
  - "src/main/java/org/brite/banking/rules/*LoginProperties.java"
---
# Banking: employees, logins, login status and who-did-what

## Model (who is who)
| Concept | Where | Notes |
|---|---|---|
| Employee profile | `bank_employees` (`EmployeeEntity`, domain `Employee`) | `employeeNumber` `EMP-000010`, `role`, `status` (employment: `ACTIVE`/`ON_LEAVE`/`TERMINATED`), `bankLocationId`, `region` (area managers), `supervisorId`. Ids are plain columns, **not foreign keys**. |
| Role → privileges | `EmployeeRole.getPrivileges()` | **Derived, never stored.** `TELLER` view/deposit/withdraw; `MANAGER` + suspend/update-suspension/reactivate/close/branch reports/`MANAGE_CUSTOMER_LOGINS`; `AREA_MANAGER` + `MANAGE_EMPLOYEES`. Each role includes the one below. |
| Employee login | `bank_employee_credentials` | Own table, one row per employee (`employeeId`), lowercase `username`, BCrypt `passwordHash`, failure/lock/last-login columns, `status`. |
| Customer login | `customer_credentials` | Same shape, keyed by `customerId`. |
| Login status | `LoginStatus` `ACTIVE`/`INACTIVE`/`LOCKED`/`SUSPENDED` | **Only `ACTIVE` may transact.** |
| Who handled a transaction | `TransactionHandler` → `account_transactions` columns | Employee number/name/role + branch/ATM id/name/type/city/state, snapshotted; null for customer-initiated. |

## Rules (do not break)
1. **An employee acts only if their employment status is ACTIVE *and* they have an ACTIVE login with the role's privilege** (`EmployeeService.requirePrivilege`; a missing login is rejected). Every staff endpoint goes through it *before* any service is called, and a rejected call must leave nothing changed (tests assert `never()`/`verifyNoInteractions`).
2. **Customer-initiated deposit/withdraw require an ACTIVE login if the owner has one** (`ClientAccountService` → `CustomerCredentialService.requireActiveLoginIfPresent`, only when there is no `TransactionHandler`). Kept as defence in depth: the customer endpoints now require a token (rule 9), so a customer without a login can't get that far.
3. Staff-handled transactions are gated on the employee, not the customer's login.
4. Passwords are **exactly 8 digits**, stored only as BCrypt hashes (`spring-security-crypto`; no Spring Security web stack). Usernames: `^[a-z0-9._-]{3,50}$`, lowercase, case-insensitive on login. Shared logic lives in `LoginSupport` (package-private); both credential services use it so they cannot drift.
5. **8 digits is only 10^8 combinations; the lockout is the protection.** Wrong passwords on an ACTIVE login count; at `banking.employee-login.max-failed-attempts` / `banking.customer-login.max-failed-attempts` (5) it becomes `LOCKED` until `now + lockout-minutes` (15). A lock past its `lockedUntil` counts as ACTIVE (`LoginState.effectiveStatus`) even before the next login clears it. An administrator's `LOCKED` has no expiry. `INACTIVE`/`SUSPENDED` are administrator-only and **a wrong password never changes them**.
6. Unknown username and wrong password give the same message and cost the same (dummy hash). While locked the password isn't checked. A right password on an `INACTIVE`/`SUSPENDED` login → `LoginNotActiveException` (403).
7. `verify` is `@Transactional(noRollbackFor = ...)` for every exception it throws on purpose, otherwise the failed-attempt count would roll back. Anything new thrown from `verify` must be added there. `EmployeeCredentialPersistenceTest`/`CustomerCredentialPersistenceTest` run outside a test transaction to prove it.
8. Never log, return, or put in an exception/URL a password or hash. `@ToString(exclude = "passwordHash")`/`"password"`, and `BankingRequestLoggingFilter.mask` masks `password`.
9. **Both tokens are enforced: the employee JWT on staff endpoints, the customer JWT on the customer account/portal endpoints.** `POST /v1/api/staff/login` and `/customers/login` return a signed JWT (`LoginService` → `JwtService`; HS256, `iss`/`sub`/`type`/`role`/`jti`/`iat`/`exp`, 30 min, no refresh). `StaffAuthenticationFilter` (gateway, after the rate limiter) demands `Authorization: Bearer` on every `/v1/api/staff/*` path except exactly `POST /v1/api/staff/login` and hands the token's subject to `StaffController` through a request attribute — there is no `X-Employee-Number` header any more. **The token proves who logged in, nothing else:** permissions, employment status, login status and role are reloaded by `EmployeeService.requirePrivilege` on every request, so suspending a login or demoting someone applies at once (never copy `role` from the token into an authorisation decision). A customer token on a staff path is 403. `CustomerAuthenticationFilter` does the same for `/v1/api/accounts/*` and `/bff/v1/portal/*` (except exactly `POST /v1/api/accounts/newaccount` and `POST /bff/v1/portal/accounts/open`): the token's subject is the customer id, the customer's login is re-checked ACTIVE on every request (`CustomerCredentialService.requireActiveLogin`), an employee token is 403, and `CustomerAccessService` keeps every account number in the path/body (and the account list / portal home, filtered in SQL by `customerId`) to the caller's own accounts: someone else's → 403, bulk close refused as a whole, no authenticated customer → fails closed 401. Rule 2 is now defence in depth, since a customer without a login can't reach those endpoints at all.
10. **Token handling:** the secret is `BANKING_JWT_SECRET` (>= 32 chars, never in a committed file or test other than a throwaway test string); blank means a random key per start (warned). No personal data or passwords in claims. Tokens are credentials: never log them (`IssuedToken`/login responses exclude them from `toString`), login responses are `Cache-Control: no-store`, and no token is issued unless `verify` succeeded.

## Endpoints (all under `BANKING_URL_PATTERNS`)
- `POST /v1/api/staff/login`, `POST /v1/api/customers/login` (`LoginController` → `LoginService`): 200 `{accessToken, tokenType, expiresIn, employee|customer}`, 401 generic, 423 locked, 403 not active, 400 missing field.
- `POST /v1/api/staff/customers/{id}/login` (`MANAGE_CUSTOMER_LOGINS`, 201): a manager creates a customer's login; customers need one to use the account/portal endpoints and registration doesn't create it.
- `POST /v1/api/staff/accounts/{withdraw,deposit}` (`?locationId=`), `/{n}/suspend`, `PATCH /{n}/suspension`, `/{n}/reactivate`, `/{n}/close`; `GET /v1/api/staff/employees[/{n}]`; `PUT /v1/api/staff/employees/{n}/login-status` (`MANAGE_EMPLOYEES`) and `PUT /v1/api/staff/customers/{id}/login-status` (`MANAGE_CUSTOMER_LOGINS`).
- Orchestration: `StaffAccountService` (privilege check → delegate to `ClientAccountService`/`AccountSuspensionService`, then log `LOG_STAFF_ACTION`), `StaffLoginService` (status changes), `EmployeeService` (checks, profile reads). Business rules stay in the delegated services; the customer and portal endpoints keep their rules and only gain authentication and ownership checks. Suspend, update-suspension and reactivate exist only as staff endpoints (the customer/portal routes were removed, so a customer can't lift a manager's suspension); a customer can still close their own account.
- Location for a staff deposit/withdraw: `?locationId=` else the employee's own branch (none for an area manager who passes none); unknown id → 404 before anything is deposited.

## Adding to this area
- **New privilege**: enum value → decide which roles get it in `EmployeeRole` → endpoint calls `requirePrivilege` first → OpenAPI `EmployeePrivilege` enum + role description → tests for allowed and forbidden roles. Privileges are not stored, so there is no DB change.
- **New `LoginStatus` value**: enum + `db/ddl` migration (enum column on both tables) + `LoginSupport` + OpenAPI `LoginStatus` + decide how it behaves in `effectiveStatus` and `verify`.
- **New login-bearing actor**: copy the credential pattern (own table, plain id, `LoginState`, `LoginSupport`), its seeder (DEMO ONLY, run-once), `db/data` mirror, properties, and the masking/test checklist above.
- Demo data: employees `db/data/07`, employee logins `08` (username = email local part, password `2026` + 4-digit employee sequence), customer logins `09` (first 10 customers: `customer0001` / `20260001`). Demo only; never reuse the scheme for real people.
