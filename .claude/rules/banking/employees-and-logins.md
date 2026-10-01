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
2. **Customer-initiated deposit/withdraw require an ACTIVE login if the owner has one** (`ClientAccountService` → `CustomerCredentialService.requireActiveLoginIfPresent`, only when there is no `TransactionHandler`). A customer with *no* login is not restricted, because the customer/portal endpoints don't require a login yet; tighten this only together with a real customer authentication.
3. Staff-handled transactions are gated on the employee, not the customer's login.
4. Passwords are **exactly 8 digits**, stored only as BCrypt hashes (`spring-security-crypto`; no Spring Security web stack). Usernames: `^[a-z0-9._-]{3,50}$`, lowercase, case-insensitive on login. Shared logic lives in `LoginSupport` (package-private); both credential services use it so they cannot drift.
5. **8 digits is only 10^8 combinations; the lockout is the protection.** Wrong passwords on an ACTIVE login count; at `banking.employee-login.max-failed-attempts` / `banking.customer-login.max-failed-attempts` (5) it becomes `LOCKED` until `now + lockout-minutes` (15). A lock past its `lockedUntil` counts as ACTIVE (`LoginState.effectiveStatus`) even before the next login clears it. An administrator's `LOCKED` has no expiry. `INACTIVE`/`SUSPENDED` are administrator-only and **a wrong password never changes them**.
6. Unknown username and wrong password give the same message and cost the same (dummy hash). While locked the password isn't checked. A right password on an `INACTIVE`/`SUSPENDED` login → `LoginNotActiveException` (403).
7. `verify` is `@Transactional(noRollbackFor = ...)` for every exception it throws on purpose, otherwise the failed-attempt count would roll back. Anything new thrown from `verify` must be added there. `EmployeeCredentialPersistenceTest`/`CustomerCredentialPersistenceTest` run outside a test transaction to prove it.
8. Never log, return, or put in an exception/URL a password or hash. `@ToString(exclude = "passwordHash")`/`"password"`, and `BankingRequestLoggingFilter.mask` masks `password`.
9. Identity today is a **claim** (`X-Employee-Number`, `X-Customer-Id`). `POST /v1/api/staff/login` and `/customers/login` return a signed **JWT** (`LoginService` → `JwtService`; HS256, `iss`/`sub`/`type`/`role`/`jti`/`iat`/`exp`, 30 min, no refresh) but **nothing requires it yet**. Don't describe the headers as authentication, and don't describe the token as enforced. Enforcing it means a bearer filter in front of the rate limiter that calls `JwtService.parse`, re-checks the login/employee status (a token outlives a suspension for up to 30 minutes otherwise), and replaces the two headers.
10. **Token handling:** the secret is `BANKING_JWT_SECRET` (>= 32 chars, never in a committed file or test other than a throwaway test string); blank means a random key per start (warned). No personal data or passwords in claims. Tokens are credentials: never log them (`IssuedToken`/login responses exclude them from `toString`), login responses are `Cache-Control: no-store`, and no token is issued unless `verify` succeeded.

## Endpoints (all under `BANKING_URL_PATTERNS`)
- `POST /v1/api/staff/login`, `POST /v1/api/customers/login` (`LoginController` → `LoginService`): 200 `{accessToken, tokenType, expiresIn, employee|customer}`, 401 generic, 423 locked, 403 not active, 400 missing field.
- `POST /v1/api/staff/accounts/{withdraw,deposit}` (`?locationId=`), `/{n}/suspend`, `PATCH /{n}/suspension`, `/{n}/reactivate`, `/{n}/close`; `GET /v1/api/staff/employees[/{n}]`; `PUT /v1/api/staff/employees/{n}/login-status` (`MANAGE_EMPLOYEES`) and `PUT /v1/api/staff/customers/{id}/login-status` (`MANAGE_CUSTOMER_LOGINS`).
- Orchestration: `StaffAccountService` (privilege check → delegate to `ClientAccountService`/`AccountSuspensionService`, then log `LOG_STAFF_ACTION`), `StaffLoginService` (status changes), `EmployeeService` (checks, profile reads). Business rules stay in the delegated services; the existing customer and portal endpoints are deliberately unchanged.
- Location for a staff deposit/withdraw: `?locationId=` else the employee's own branch (none for an area manager who passes none); unknown id → 404 before anything is deposited.

## Adding to this area
- **New privilege**: enum value → decide which roles get it in `EmployeeRole` → endpoint calls `requirePrivilege` first → OpenAPI `EmployeePrivilege` enum + role description → tests for allowed and forbidden roles. Privileges are not stored, so there is no DB change.
- **New `LoginStatus` value**: enum + `db/ddl` migration (enum column on both tables) + `LoginSupport` + OpenAPI `LoginStatus` + decide how it behaves in `effectiveStatus` and `verify`.
- **New login-bearing actor**: copy the credential pattern (own table, plain id, `LoginState`, `LoginSupport`), its seeder (DEMO ONLY, run-once), `db/data` mirror, properties, and the masking/test checklist above.
- Demo data: employees `db/data/07`, employee logins `08` (username = email local part, password `2026` + 4-digit employee sequence), customer logins `09` (first 10 customers: `customer0001` / `20260001`). Demo only; never reuse the scheme for real people.
