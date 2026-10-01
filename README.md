# bankingservices (Brite Technology Notifications)

A Spring Boot 3 REST application demonstrating configuration properties binding (`@ConfigurationProperties`), custom REST controllers, async processing, global exception handling, Spring Data JPA, and Spring Boot Actuator monitoring.

---

## 🚀 Features

- **Configuration Management**: Strongly-typed properties bound via `@ConfigurationProperties` for notification options (App, Email, SMS, Retry).
- **Configs API**: moved to the separate `configservice` project (`/v1/configs`, port 8083).
- **Banking APIs**: Client/account lookup, registration, withdrawal, and deposit (`/v1/client`, `/v1/api/accounts`) — accounts, customers, transactions, and withdrawal history are persisted via Spring Data JPA to the same MySQL database as the events module (see below), so data survives app restarts — plus async notification demos (`/notify`, `/report`) backed by `@Async`. Account numbers are always `CH-`/`SV-` (checking/savings) followed by a zero-padded 10-digit number (e.g. `CH-0000088291`), whether seeded or generated on registration. 52 demo accounts (26 checking, 26 savings) are seeded on first startup against an empty database (see [Data Model](#-banking-data-model-jpa) below).
- **Staff, logins & login status**: bank employees (`TELLER` / `MANAGER` / `AREA_MANAGER`) with role-derived privileges, employee and customer logins (username + 8-digit BCrypt-hashed password, each in its own table, lockout after repeated failures), a login status (`ACTIVE`/`INACTIVE`/`LOCKED`/`SUSPENDED`) where **only `ACTIVE` may transact**, staff endpoints that enforce the privileges and record who/where on each transaction, and `POST /v1/api/staff/login` / `/v1/api/customers/login` — see [Staff, logins & login status](#staff-logins--login-status-design). The login endpoints return a signed JWT: every staff endpoint except the login requires the employee token, and the customer account/portal endpoints require the customer token and only reach the caller's own accounts (`Authorization: Bearer`).
- **Banking API Gateway & Rate Limiter**: Every banking endpoint (`/v1/api/accounts/**`, `/v1/api/locations/**`, `/v1/api/staff/**`, `/v1/api/customers/login`, `/v1/client/**`, `/v1/payment/**`, `/notify`, `/notify-sms`, `/report`, `/bff/v1/portal/**`) sits behind a `Filter`-based gateway ingress layer that requires an `X-Customer-Id` header and caps each customer to a configurable number of requests per day (`banking.rate-limit`, default 1000/day) — see [Banking API gateway](#-banking-api-gateway--rate-limiter) below. Events/configs endpoints are unaffected.
- **Business Transaction ID (btid) Tracing**: The same gateway stamps every banking request with a unique `btid` (`X-BTID` response header) before it reaches any controller. The id is stored in SLF4J's MDC, so every log line from every layer of that request — controller, service, repository — carries it, letting you grep one request's full log trail with a single id.
- **Account Constraints**: Configurable business rules (`banking.constraints`) enforced on registration/withdrawal/deposit — minimum age to open an account, minimum balance retained after a withdrawal (checking/savings), and a maximum single cash-deposit amount.
- **Bank Statement**: `/v1/api/accounts/{accountNumber}/statement` returns an account's deposit/withdrawal history for a given date range, capped by a configurable maximum range in months.
- **Resilience Demo**: `/v1/payment/process` demonstrates a Resilience4j circuit breaker with jittered exponential-backoff retry around a simulated flaky downstream call.
- **Event Ingestion**: moved to the separate `eventservice` project (`/api/events`, port 8085); it keeps using the existing `events` table in `db_example`.
- **API Versioning Demo**: moved to the separate `restapiversionservice` project (`/apiversion`, port 8084).
- **Actuator Monitoring**: Integrated Spring Boot Actuator exposing health status under `/actuator/health`.
- **Endpoint Execution-Time Logging**: A Spring AOP `@Aspect` (`ExecutionTimeLoggingAspect`, `org.brite.common.logging`) wraps every `@RestController` method app-wide and logs its execution time in milliseconds — no code changes needed per controller.
- **Account Search Caching**: `GET /v1/api/accounts` results are cached for 10 minutes via Spring's `@Cacheable` (Caffeine, `spring.cache.caffeine.spec: expireAfterWrite=10m`), evicted whenever an account is registered or closed.
- **Global Exception Handling**: Centralized exception handling using `@ControllerAdvice`, with all exception and validation messages centralized in `BankingMessages`.
- **Structured Logging**: Slf4j logging across the banking module — info logs for successful operations, warn logs for validation/business rejections (insufficient funds, account not found, etc.) — with log message templates also centralized in `BankingMessages`.
- **Database Integration**: MySQL datasource integration with Hibernate / Spring Data JPA.

---

## 🛠️ Prerequisites & Technology Stack

- **Java**: 25 (JDK 25 is required to build and run; `java.version` in `pom.xml` is 25, so the classes are Java 25 bytecode and older JDKs can't load them)
- **Spring Boot**: 3.5.16 (Spring Framework 6.2.x, Hibernate 6.6.x, Spring Cloud 2025.0.3) — 3.5.x is the line that supports Java 25; Boot 3.4 can't read Java 25 class files
- **Build Tool**: Maven 3.9+ (run it on JDK 25: `mvn -v` should report `Java version: 25.x`)
- **Lombok**: 1.18.48, **MapStruct**: 1.6.3, **JaCoCo**: 0.8.15 (pinned in `pom.xml`)
- **Database**: MySQL 8.x

---

## ⚙️ Configuration Properties (`application.yml`)

The application runs on port **`8081`** with a base servlet context path **`/brite`**.

```yaml
server:
  port: 8081
  servlet:
    context-path: /brite

logging:
  pattern:
    console: "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %5p ${PID:- } --- [%15.15t] [btid=%X{btid:--}] %-40.40logger{39} : %m%n%wEx"

management:
  endpoints:
    web:
      base-path: /actuator
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always
      group:
        readiness:
          include: readinessState,db

spring:
  profiles:
    active: dev
  cache:
    type: caffeine
    cache-names: accountSearch
    caffeine:
      spec: expireAfterWrite=10m
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/db_example?useSSL=false
    username: root
    password: <password>

banking:
  constraints:
    maximum-deposit-amount-by-cash: 5000.00
    minimum-age: 18
    checking-minimum-balance: 25.00
    saving-minimum-balance: 100.00
    max-statement-range-months: 18
  rate-limit:
    enabled: true
    requests-per-day: 1000
    customer-header-name: X-Customer-Id
  jwt:                     # login tokens (HS256)
    secret: ${BANKING_JWT_SECRET:}   # >= 32 chars (openssl rand -base64 48); blank = random key per start, tokens die on restart
    issuer: bankingservices
    expiration-minutes: 30
  password-reset:          # wrong security answers before the reset locks for that login
    max-failed-attempts: 3
    lockout-minutes: 30
  employee-login:          # wrong passwords before an employee login locks, and for how long
    max-failed-attempts: 5
    lockout-minutes: 15
  customer-login:          # same for customer logins
    max-failed-attempts: 5
    lockout-minutes: 15
  suspension:
    expiry-job:
      enabled: true        # reactivates suspensions whose end has passed
      interval-ms: 60000

resilience4j:
  circuitbreaker:
    instances:
      bankService:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 5s
        permittedNumberOfCallsInHalfOpenState: 3
  retry:
    instances:
      bankService:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        enableRandomizedWait: true   # adds jitter to the backoff
        randomizedWaitFactor: 0.5
```

---

## 🌐 API Endpoints

All REST endpoints are prefixed with `http://localhost:8081/brite`:

### Banking — clients, accounts & notifications

> Every endpoint below requires an `X-Customer-Id` header and is subject to the per-customer daily rate limit — see [Banking API gateway & rate limiter](#banking-api-gateway--rate-limiter).
>
> **Authentication.** Customer endpoints under `/v1/api/accounts/**` and `/bff/v1/portal/**` require `Authorization: Bearer <customer token>` from `POST /v1/api/customers/login` — except the two that create a customer (`POST /v1/api/accounts/newaccount`, `POST /bff/v1/portal/accounts/open`) — and a customer can only reach **their own accounts** (another customer's account is `403`; the account list and the portal home show only the caller's). Staff endpoints under `/v1/api/staff/**` require the employee token (see below). Branch locations and the demo endpoints (`/v1/client`, `/v1/payment`, `/notify`, `/report`) stay open. A customer needs a login first (a manager creates one with `POST /v1/api/staff/customers/{customerId}/login`). `401` = no/invalid/expired token, `403` = someone else's account, an employee token, or a login that isn't `ACTIVE`. `X-Customer-Id` is still required everywhere as the rate-limit key.

| Method | Endpoint Path | Description |
| :--- | :--- | :--- |
| `GET` | `/v1/client/name` | Returns a sample customer record |
| `GET` | `/v1/api/accounts?accountNumber=&status=&createdFrom=&createdTo=&closedFrom=&closedTo=&months=&page=&size=&sort=` | Retrieves account ids/details within a createdDate/closedDate range, paginated (backed by a real JPA `Specification` query, cached for 10 minutes). All filters optional. **Default lookback**: if neither `createdFrom` nor `createdTo` is given, defaults to "as of today minus `months` months" (18 months if `months` is also omitted); supplying either explicit created-date bound disables this default and `months` is ignored (`400` if `months` isn't positive). **Conditional lookup**: if `accountNumber` is given, only that account is returned (still subject to the other filters — an out-of-range match yields an empty page, not a bypass); if omitted/null, every matching account is returned. Other filters: `status` (`ACTIVE`/`CLOSED`), `closedFrom`/`closedTo` (inclusive `yyyy-MM-dd` range), standard Spring Data `page`/`size`/`sort` (sortable by `createdDate`, `closedDate`, `accountStatus`, `accountNumber`; default `size=20`, sorted by `createdDate` ascending). Returns a Spring Data `Page<AccountStatusView>` envelope (`content`, `totalElements`, `totalPages`, etc) — each row is flattened to `accountNumber`, `accountType`, `accountStatus`, `createdDate`, `closedDate`, `firstName`, `lastName`, not the full nested `Account`/`Customer`. `400` if a `*From` date is after its `*To` date or an unsupported `sort` property is given |
| `POST` | `/v1/api/accounts/lookup` | Looks up an account by account number; `404` if not found |
| `POST` | `/v1/api/accounts/newaccount` | Registers a new customer + account. `phoneNumber` is required and must be `###-###-####` (`400` otherwise); it is stored on the customer and returned in the `Account` response. The request logger masks it (`***`) like `dateOfBirth` |
| `POST` | `/v1/api/accounts/withdraw` | Withdraws funds from a checking/savings account; `400` on insufficient funds, mismatched account type, a `CLOSED` or `SUSPENDED` account, `404` if the account doesn't exist |
| `POST` | `/v1/api/accounts/deposit` | Deposits funds into a checking/savings account; `400` on invalid amount/deposit type, mismatched account type, a cash amount over the configured maximum, or a `CLOSED` or `SUSPENDED` account, `404` if the account doesn't exist |
| `POST` | `/v1/api/accounts/{accountNumber}/close` | Closes a checking/savings account (status `ACTIVE` or `SUSPENDED` → `CLOSED`, stamps `closedDate`, clears any suspension); `400` if already closed, `404` if the account doesn't exist |
| `POST` | `/v1/api/staff/accounts/withdraw` · `/deposit` | **Employee-facing.** Same body and rules as the customer endpoints, but the acting employee is the subject of the `Authorization: Bearer` token from the staff login (`401` without a valid one, `403` for a customer token) and must be `ACTIVE` with `WITHDRAW` / `DEPOSIT` (tellers and up). The transaction records the employee and the branch/ATM (`?locationId=`, default the employee's own branch; none for an area manager who omits it; `404` if unknown) and they show on the statement. `403` if not allowed, `404` unknown employee, `400` missing header |
| `POST` / `PATCH` | `/v1/api/staff/accounts/{accountNumber}/suspend` · `/suspension` (PATCH) · `/reactivate` · `/close` | **Suspending, updating a suspension and reactivating are staff-only** — customers have no endpoint for them (the customer and portal APIs return `404`); closing an account is also available to the owner via `POST /v1/api/accounts/{accountNumber}/close`. Needs `SUSPEND_ACCOUNT` / `UPDATE_SUSPENSION` / `REACTIVATE_ACCOUNT` / `CLOSE_ACCOUNT` (managers and up; a teller gets `403`); the check runs before anything is changed. **Suspend** (`POST .../suspend`): body `notes` (required, max 500), optional `startDateTime` (ISO local date-time; defaults to now, not in the future) and `endDateTime` (omit = indefinite; after the start and in the future); an `ACTIVE` account becomes `SUSPENDED` and **rejects every withdraw and deposit (`400`) until it is reactivated** or `endDateTime` passes (the expiry job, every 60 s, reactivates it); `400` if closed, already suspended or the window is invalid, `404` unknown account. **Update** (`PATCH .../suspension`): `notes` and/or `endDateTime` (only supplied fields change, at least one; the new end must be in the future and after the stored start); `400` if the account isn't suspended. **Reactivate** (`POST .../reactivate`): `SUSPENDED` → `ACTIVE`, clearing the suspension flag, dates and notes; `400` if not suspended |
| `GET` | `/v1/api/staff/employees?role=&page=&size=&sort=` | Lists employee profiles; needs `MANAGE_EMPLOYEES` (area managers). Sortable by `lastName` (default), `firstName`, `employeeNumber`, `role`, `hireDate` |
| `POST` | `/v1/api/staff/login` | Body `{username, password}` (8-digit password). Verifies an employee login and returns `{accessToken, tokenType: "Bearer", expiresIn, employee}` — a signed JWT (HS256, 30 min, no refresh) plus the `Employee` profile with its role `privileges`. `401` unknown user or wrong password (same message), `423` locked (5 wrong passwords lock it for 15 min), `403` login `INACTIVE`/`SUSPENDED` or employee not `ACTIVE`, `400` missing field. **Every other staff endpoint requires this token** as `Authorization: Bearer <token>`; the response is `Cache-Control: no-store`. The password is never logged (the request logger masks it) or returned |
| `POST` | `/v1/api/customers/login` | Same body and error codes; returns `{accessToken, tokenType, expiresIn, customer: {customerId, firstName, lastName}}`. **The token is required by the customer account and portal endpoints.** Demo login: `customer0001` / `20260001` (customer 1 owns `CH-0000088291`) |
| `GET` | `/v1/api/staff/employees/{employeeNumber}` | One profile with its derived `privileges`; an employee can read their own, anyone else's needs `MANAGE_EMPLOYEES` |
| `PUT` | `/v1/api/staff/employees/{employeeNumber}/login-status` | Body `{status, reason?}` (`status` = `ACTIVE`/`INACTIVE`/`LOCKED`/`SUSPENDED`, reason ≤ 200 chars). Sets an employee's login status; needs `MANAGE_EMPLOYEES` (area managers). **Only an `ACTIVE` login may perform transactions** (an employee with an inactive, suspended or locked login, or none, gets `403` on every staff endpoint). `ACTIVE` also clears the failure count and any lock |
| `POST` | `/v1/api/staff/customers/{customerId}/login` | Body `{username, password}` (username `^[a-z0-9._-]{3,50}$`, password exactly 8 digits). Creates a customer's login (starts `ACTIVE`); needs `MANAGE_CUSTOMER_LOGINS` (managers and up). `201 {username, status}`; `400` bad format / taken username / customer already has a login; `404` unknown customer. Customers need a login to use the protected account and portal endpoints; the password is masked in logs and never returned |
| `PUT` | `/v1/api/staff/customers/{customerId}/password` | Body `{newPassword}` (exactly 8 digits). Administrator sets a customer's password (e.g. when no security questions were set); needs `MANAGE_CUSTOMER_LOGINS` (managers and up). `204`; clears the login failure and reset counters, leaves the status alone, and **every token the customer holds stops working**. `404` unknown customer/no login |
| `PUT` | `/v1/api/staff/employees/{employeeNumber}/password` | Same, for an employee; needs `MANAGE_EMPLOYEES` (area managers) |
| `POST` | `/v1/api/customers/password-reset/questions` · `/v1/api/staff/password-reset/questions` | **Open (no token).** Body `{username}`. Returns the user's three security questions as `[{question, text}]` (`FIRST_CAR` "What was your first car?", `FIRST_SCHOOL`, `FIRST_TEACHER`, `FIRST_PET`, `BIRTH_CITY`, `CHILDHOOD_FRIEND`). An unknown username, or a user with no questions, gets three **decoy** questions, so it doesn't reveal whether the account exists |
| `POST` | `/v1/api/customers/password-reset` · `/v1/api/staff/password-reset` | **Open (no token).** Forgotten-password reset: body `{username, answers: [{question, answer} x3], newPassword}` (new password exactly 8 digits). All three answers must be right (case-insensitive, whitespace collapsed; stored only as BCrypt hashes). `204`; `401` `Invalid username or answers` for an unknown user, no questions set or wrong answers (one message); `423` after 3 wrong answers (reset locked 30 min, `banking.password-reset.*`); `403` if the answers are right but the login isn't `ACTIVE` (a reset never undoes an administrator's status); `400` bad new password or answer count. On success **every token issued before now stops working** |
| `PUT` | `/v1/api/customers/security-questions` · `/v1/api/staff/security-questions` | Needs the caller's token (customer / employee). Body `{currentPassword, answers: [{question, answer} x3]}`: pick **three different** questions from the catalog and answer each (2-100 chars); replaces an earlier choice in place; the current password is required so a stolen token can't change them. Returns the chosen questions (never the answers). `401` wrong current password |
| `PUT` | `/v1/api/staff/customers/{customerId}/login-status` | Same body; sets a customer's login status; needs `MANAGE_CUSTOMER_LOGINS` (managers and up). A customer whose login isn't `ACTIVE` gets `403` on every customer endpoint (the login is re-checked on each request, so it applies at once even with an unexpired token). `404` for an unknown customer or one without a login |
| `POST` | `/v1/api/accounts/close` | Bulk-closes multiple accounts in one call (body: `{"accountNumbers": [...]}`). Best-effort — an invalid/already-closed account number doesn't block the others; the `200` response carries `closedAccounts` (the ones that succeeded) and `failures` (`accountNumber` + `reason` for the rest). `400` if `accountNumbers` is empty/missing |
| `GET` | `/v1/api/accounts/{accountNumber}/statement?beginDate=yyyy-MM-dd&endDate=yyyy-MM-dd` | Returns a bank statement (deposit/withdrawal history) for the account in the given range; each transaction includes `depositType` (`"cash"`/`"check"` for deposits, `null` for withdrawals); `400` if the range exceeds the configured maximum months, `404` if the account doesn't exist |
| `GET` | `/v1/api/locations?type=&city=&state=&zip=&service=&page=&size=&sort=` | Searches bank offices/ATMs, paginated (real JPA `Specification` query, not cached). All filters optional and AND'd; `city`/`state`/`zip` are case-insensitive exact matches. `type` matches by capability: `OFFICE` returns offices **and** office+ATM branches, `ATM` returns ATMs **and** office+ATM branches, `BOTH` only the branches with both. `service` is one of `BANKING`, `SAFE_DEPOSIT_LOCKER`, `LOANS_MORTGAGES`, `NOTARY`, `WIRE_TRANSFER`, `FOREIGN_EXCHANGE`, `ATM_CASH_WITHDRAWAL`, `ATM_DEPOSIT`. Sortable by `name` (default), `locationType`, `city`, `state`; default `size=20`. Each row has `name`, `bankAddress`, `locationType`, office `opensAt`/`closesAt` (`08:00:00`/`16:00:00`, wall clock in `timeZone` `America/Chicago`), office `phoneNumber` and `services` — hours and phone are `null` for ATM-only rows. `400` for an unsupported `sort` property or unknown `type`/`service` |
| `GET` | `/v1/api/locations/{id}` | Returns one bank office/ATM by id (same shape as a search row); `404` if no location has that id, `400` if `id` isn't numeric |
| `GET` | `/bff/v1/portal/home?state=` | **BFF for the banking UI portal** (customer token required; shows only the caller's accounts). One call for the home screen: the newest `ACTIVE` and `SUSPENDED` accounts merged (max 5, closed omitted, no balances; each row has `suspended`/`suspendedUntil`) plus branches/ATMs (max 5, sorted by name; filtered by `state` if given). Returns `totalActiveAccounts`, `totalSuspendedAccounts`, `accounts`, `nearbyLocations` |
| `GET` | `/bff/v1/portal/accounts/{accountNumber}/overview?days=` | Account detail: balance plus transactions from the last `days` days (default 30, `1`–`90`), newest first, max 20. Returns `maskedPhoneNumber` (`***-***-0101`, last four digits only; the BFF never returns a full number). Read-only (no email/SMS, unlike the statement endpoint). `404` unknown account, `400` bad `days` |
| `POST` | `/bff/v1/portal/accounts/open` | Same body (incl. required `phoneNumber`), rules and side effects as `POST /v1/api/accounts/newaccount`, plus `nearbyLocations` in the customer's state. `201` |
| `POST` | `/bff/v1/portal/accounts/withdraw` | Same body/rules/side effects as `POST /v1/api/accounts/withdraw`, but returns the **refreshed account overview** (balance, suspension state, recent activity). `400` (plain text) if the account is `CLOSED` or `SUSPENDED`, on insufficient funds, etc. |
| `POST` | `/bff/v1/portal/accounts/deposit` | Same as `POST /v1/api/accounts/deposit`, returning the refreshed overview; `400` if `CLOSED` or `SUSPENDED` |
| `POST` | `/bff/v1/portal/accounts/{accountNumber}/close` | Same rules as the banking close endpoint (irreversible); returns the refreshed overview (`CLOSED`, `closedDate`). `400` if already closed |
| `POST` | `/bff/v1/portal/accounts/{accountNumber}/statement?beginDate=&endDate=` | Same rules and result as the banking statement endpoint, **including its email/SMS notification**; a `POST` because portal reads are side-effect free. Returns the `BankStatement` |
| `GET` | `/notify?name={name}` | Fire-and-forget async email notification demo |
| `GET` | `/report` | Async task that returns a completed report string |

### Banking API gateway & rate limiter

`BankingRateLimitFilter` (`org.brite.banking.gateway`) is a servlet `Filter` registered only for the banking module's URL patterns (`/v1/api/accounts/*`, `/v1/api/locations`, `/v1/api/locations/*`, `/v1/client/*`, `/v1/payment/*`, `/notify`, `/notify-sms`, `/report`, `/bff/v1/portal/*`, `/v1/api/staff/*`, `/v1/api/customers/*`) — it runs before `DispatcherServlet`, so rejected requests never reach a controller. It acts as a lightweight API-gateway ingress layer with two responsibilities:

1. **Customer identification** — every request must carry the header configured by `banking.rate-limit.customer-header-name` (default `X-Customer-Id`). Missing/blank header → `400` with a plain-text explanation.
2. **Per-customer daily rate limit** — each customer ID is capped at `banking.rate-limit.requests-per-day` (default **1000**) requests per calendar day, tracked in-memory and reset at midnight. Exceeding it → `429 Too Many Requests`.
3. **JWT authentication** (after the rate limiter, before request logging) — `StaffAuthenticationFilter` on `/v1/api/staff/*` and `CustomerAuthenticationFilter` on `/v1/api/accounts/*` and `/bff/v1/portal/*` demand a valid bearer token of the right kind (see [Staff, logins & login status](#staff-logins--login-status-design)); `401`/`403` plain text, so rejected requests never reach a controller.

Every response that reaches the filter (allowed or rejected) carries `X-RateLimit-Limit` and `X-RateLimit-Remaining` headers. Set `banking.rate-limit.enabled: false` to bypass the whole gateway (e.g. for local scripting). The counters themselves are a single-instance, in-memory `ConcurrentHashMap` (unlike account/customer/transaction data, which is now persisted via JPA — see [Data Model](#-banking-data-model-jpa)) — not a distributed rate limiter — and the `X-Customer-Id` rate-limit key is still a caller-supplied header rather than an authenticated principal (the JWT, not that header, is what authenticates a customer).

A second filter, `BusinessTransactionIdFilter`, is registered on the same URL patterns but runs *first* (ahead of the rate limiter), so it stamps a unique business transaction id (`btid`, a UUID) onto every banking request before anything else touches it — including requests the rate limiter goes on to reject. The `btid` is put into SLF4J's MDC and echoed back as the `X-BTID` response header; every log line for that request, in every layer (controller, service, repository), automatically includes `[btid=...]` via the `logging.pattern.console` entry in `application.yml` — no parameter threading required. It's cleared from MDC in a `finally` block after each request so it never leaks onto Tomcat's reused worker threads. Logs outside any request (startup, scheduled tasks) show `[btid=-]`.

### Banking data model (JPA)

Banking used to be a `ConcurrentHashMap`-backed mock store; it's now backed by real JPA entities persisted to the same MySQL database (`db_example`) as the (separate) eventservice. The service/controller layers still use the same `Account`/`Customer`/`Address`/`AccountTransaction`/`WithdrawalForm` domain objects as before — the entity ↔ domain mapping is entirely internal to the repository classes, so no other code changed for this migration.

| Entity (`org.brite.banking.entity`) | Table | Notes |
| :--- | :--- | :--- |
| `AccountEntity` | `accounts` | One row per real account (checking **or** savings) — not one row per customer pairing. `@Version` column for optimistic locking on concurrent withdraw/deposit. Status is `ACTIVE`, `SUSPENDED` or `CLOSED`; suspension is stored in `suspended` (flag), `suspended_start`/`suspended_end` (datetimes, end null = indefinite) and `suspension_notes`. |
| `CustomerEntity` | `customers` | `@ManyToOne` from `AccountEntity`, cascades on save. Holds name, date of birth, `phoneNumber` (`###-###-####`, nullable for customers registered before the field existed) and the embedded address. |
| `AddressEmbeddable` | — | `@Embeddable`, inlined as columns on `CustomerEntity`/`WithdrawalHistoryEntity` — no separate table. |
| `EmployeeEntity` / `EmployeeCredentialEntity` | `bank_employees` / `bank_employee_credentials` | Staff profiles (role `TELLER`/`MANAGER`/`AREA_MANAGER`, branch, region, supervisor; privileges derive from the role) and, in a separate table, each employee's login: unique `username` plus a BCrypt `passwordHash` of an exactly-8-digit password, with failed-attempt/lock/last-login columns. `EmployeeCredentialService.verify(username, password)` checks a login (wrong password and unknown user look identical; 5 failures lock it for 15 minutes — `banking.employee-login.*`). Demo logins are seeded for all 21 employees (`lucas.meyer` / `20260010`: username = email local part, password = `2026` + 4-digit employee number). `POST /v1/api/staff/login` and `POST /v1/api/customers/login` verify it (returns a JWT; the employee one is required by the staff endpoints). Each login has a `status` (`ACTIVE`/`INACTIVE`/`LOCKED`/`SUSPENDED`); only `ACTIVE` may perform transactions. |
| `CustomerCredentialEntity` | `customer_credentials` | A customer's login in its own table, same shape and rules as the employee login (unique lowercase `username`, BCrypt `passwordHash` of an exactly-8-digit password, failed-attempt/lock/last-login columns). `CustomerCredentialService.verify(username, password)` returns only the customer's id and name; 5 wrong passwords lock it for 15 minutes (`banking.customer-login.*`). Demo logins for the first 10 customers (`customer0001` / `20260001`). `POST /v1/api/customers/login` verifies it (returns a JWT; required by the customer account/portal endpoints). |
| `AccountTransactionEntity` | `account_transactions` | One row per deposit/withdrawal, including `depositType`. Staff-handled ones also snapshot the employee (`employeeNumber`, `employeeName`, `employeeRole`) and the branch/ATM (`bankLocationId`, `Name`, `Type`, `City`, `State`); all null for customer-initiated transactions. |
| `WithdrawalHistoryEntity` | `withdrawal_history` | Separate withdrawal-specific history (write-only, nothing reads it back — same as before the migration). |
| `BankLocationEntity` | `bank_locations`, `bank_location_services` | A bank office and/or ATM: `locationType` (`OFFICE`/`ATM`/`BOTH`), address (`BankAddressEmbeddable`, inlined), office hours 08:00–16:00 in `America/Chicago` (Central time), office phone, and the set of `BankOperationServices` it serves (`BANKING`, `SAFE_DEPOSIT_LOCKER`, `LOANS_MORTGAGES`, `NOTARY`, `WIRE_TRANSFER`, `FOREIGN_EXCHANGE`, `ATM_CASH_WITHDRAWAL`, `ATM_DEPOSIT`) in the second table. ATM-only rows have no hours or phone. Domain shapes: `BankLocations`, `BankAddress`. |

Raw Spring Data repositories live in `org.brite.banking.repository.jpa` (`AccountJpaRepository` — extends `JpaSpecificationExecutor` for the dynamic account-search filtering — plus `CustomerJpaRepository`, `AccountTransactionJpaRepository`, `WithdrawalHistoryJpaRepository`); application code never touches them directly. `AccountRepository`/`TransactionRepository`/`WithdrawalRepository` (same names/packages as before) wrap them and keep their old public method signatures.

`BankLocationDataSeeder` likewise seeds 20 demo bank locations (8 office, 6 ATM, 6 office+ATM across Central-time cities) when `bank_locations` is empty. `AccountDataSeeder` seeds the 52 demo accounts on first startup, but only if the `accounts` table is empty. `AccountStatusDemoSeeder` then adds 40 more — 20 `CLOSED` (`CH-0000030001..30010`, `SV-0000040001..40010`) and 20 `SUSPENDED` (`CH-0000050001..50010`, `SV-0000060001..60010`, 14 with an end date and 6 indefinite, all with notes) — for any of those numbers that don't exist yet, so an existing database gets them on the next start (92 accounts in total). Every seeded customer has a phone number, `512-555-0001`…`0092` (customer N = `512-555-000N`); an older database gets them from `db/data/06_backfill_customer_phones.sql`. The base seeder only runs on an empty table because data now persists across restarts, so unconditional reseeding would create duplicates every time the app starts. Account numbers are still generated the same way as before (zero-padded 10-digit `CH-`/`SV-` numbers, starting from a `10001` counter), so previously-documented account numbers remain valid.

### Staff, logins & login status (design)

```
staff request ─▶ StaffAuthenticationFilter ─▶ StaffController ─▶ EmployeeService.requirePrivilege ─▶ StaffAccountService / StaffLoginService
                  (Bearer JWT)          ACTIVE employee + ACTIVE login + role privilege        │
                                                                                               ▼
customer request ─▶ CustomerAuthenticationFilter ─▶ ClientAccountController / PortalController
  (Bearer JWT)         customer login re-checked ACTIVE        └─ CustomerAccessService: only the caller's own accounts
                                                              ─▶ ClientAccountService: same business rules as always (closed/suspended/balance...)
```

| Role | Privileges (derived from the role, not stored) |
| :--- | :--- |
| `TELLER` | view account, deposit, withdraw |
| `MANAGER` | teller + suspend, update suspension, reactivate, close, branch reports, **manage customer logins** |
| `AREA_MANAGER` | manager + **manage employees** (employee profiles and employee login status) |

| Login status | Meaning | Set by |
| :--- | :--- | :--- |
| `ACTIVE` | may log in and perform transactions | default; an administrator |
| `LOCKED` | refused (`423`); automatic after 5 wrong passwords for 15 minutes, or an administrator's lock with no expiry | the system / an administrator |
| `INACTIVE`, `SUSPENDED` | refused (`403`) until set back to `ACTIVE`; a wrong password never changes them | an administrator |

- **Rule:** only an `ACTIVE` login can do its assigned transactions. Employees need an `ACTIVE` employment status **and** an `ACTIVE` login (no login at all is refused) on every staff endpoint. A customer needs an `ACTIVE` login to use any protected account/portal endpoint (re-checked on every request, so suspending a login stops an unexpired token at once); the older check in `ClientAccountService` that rejects deposits/withdrawals when the owner's login isn't `ACTIVE` stays as defence in depth.
- **Who handled it:** staff deposits/withdrawals store the employee (number, name, role) and the branch/ATM (`?locationId=`, default the employee's own branch) on the transaction; customer-initiated ones leave them empty.
- **Credentials:** username `^[a-z0-9._-]{3,50}$` (case-insensitive) and an exactly-8-digit password, stored only as a BCrypt hash in `bank_employee_credentials` / `customer_credentials`. 8 digits is only 10^8 combinations, so the lockout is the real protection. Passwords are never logged (the request logger masks `password`), returned or put in an error message; unknown user and wrong password give the same `401`.
- **Tokens:** a successful login returns a signed JWT (`HS256`, claims `iss`, `sub` = employee number / customer id, `type`, `role` for employees, `jti`, `iat`, `exp`; 30 minutes, no refresh token, no personal data). The signing secret comes from `BANKING_JWT_SECRET` (>= 32 characters, never committed); if unset, a random key is generated at startup and tokens stop working on restart. `JwtService.parse` verifies signature (HMAC only, `alg: none` refused), issuer, type and expiry.
- **Enforced on staff endpoints:** `StaffAuthenticationFilter` (after the rate limiter) requires `Authorization: Bearer <employee token>` on everything under `/v1/api/staff/*` except `POST /v1/api/staff/login`: missing/invalid/expired/forged → `401` (`WWW-Authenticate: Bearer`), a customer token → `403`. The token's `sub` is the acting employee; **the token is not trusted for permissions or status** — every request reloads the employee, their login status and role, so suspending a login or demoting an employee takes effect immediately, not when the token expires. The old `X-Employee-Number` header no longer identifies anyone. Customer endpoints are not token-protected yet: `X-Customer-Id` is still only a rate-limit key.
- **Enforced on customer endpoints:** `CustomerAuthenticationFilter` (same slot, patterns `/v1/api/accounts/*` and `/bff/v1/portal/*`) requires `Authorization: Bearer <customer token>` on everything there except exactly `POST /v1/api/accounts/newaccount` and `POST /bff/v1/portal/accounts/open` (they create the customer, who has no login yet): missing/invalid/expired/forged → `401`, an employee token → `403`, a login that is no longer `ACTIVE` → `403` (looked up on every request). The token's `sub` is the customer id, and **a customer can only reach their own accounts**: `CustomerAccessService` checks every account number in the path/body before any service runs (someone else's → `403`, a bulk close containing one → refused as a whole, an account that doesn't exist → `404` from the operation), the account list and the portal home are filtered by customer in SQL (and the cache key includes the customer, so pages are never shared). The portal's CORS config now allows the `Authorization` header. Customers get a login from a manager (`POST /v1/api/staff/customers/{id}/login`); registration does not create one. Still open: branch locations and the demo endpoints. Suspending, updating a suspension and reactivating are **staff-only** (no customer route exists, so a customer can't lift a suspension a manager applied); a customer can still close their **own** account.
- **Forgotten passwords:** a user picks three different **security questions** (first car, first school, first teacher, first pet, birth city, childhood friend) and answers them while logged in (needs the current password). To reset, the open endpoints return the user's questions and accept the answers plus a new 8-digit password. Answers are normalised and stored only as BCrypt hashes in `security_answers` (three rows per user, replaced in place, never deleted). Because answers like these are guessable, three wrong attempts lock the reset for 30 minutes (separate from the login lock), an unknown user is indistinguishable from a wrong answer (same `401`, decoy questions, same timing work), and a reset only works on an `ACTIVE` login. A reset also invalidates every token issued before it (the filters compare the token's issue time with `passwordChangedAt`). Without questions, a manager (customers) or an area manager (employees) sets the password directly. Limits: security answers are low-entropy, so a leaked database makes them brute-forceable offline; there is no email/SMS channel, so this is the only self-service path.
- **Demo data (fictional, DEMO ONLY):** 21 employees (`db/data/07`), logins for all of them (`lucas.meyer` / `20260010`: password = `2026` + 4-digit employee number, `db/data/08`) and for the first 10 customers (`customer0001` / `20260001`, `db/data/09`).

### Resilience demo — `/v1/payment`

| Method | Endpoint Path | Description |
| :--- | :--- | :--- |
| `POST` | `/v1/payment/process` | Calls a simulated flaky bank service (40% failure rate) through a Resilience4j circuit breaker + jittered exponential-backoff retry; returns a fallback message once the breaker opens |


### Sample lookup — `/v1/sample`

| Method | Endpoint Path | Description |
| :--- | :--- | :--- |
| `GET` | `/v1/sample/spl?item={item}` | Looks up a sample item count by name (e.g. `Mac`, `Dell`, `IBM`) |


### Monitoring

| Method | Endpoint Path | Description |
| :--- | :--- | :--- |
| `GET` | `/actuator/health` | Returns Spring Boot Actuator application health status (aggregates the `liveness`/`readiness` groups plus `db`, disk space, etc.) |
| `GET` | `/actuator/health/liveness` | Kubernetes liveness probe — `UP` as long as the process is running; never reflects the MySQL connection |
| `GET` | `/actuator/health/readiness` | Kubernetes readiness probe — `UP` only while the app's readiness state is `ACCEPTING_TRAFFIC` **and** the MySQL `db` health indicator is `UP` |

### API documentation (Swagger UI / OpenAPI)

| Method | Endpoint Path | Description |
| :--- | :--- | :--- |
| `GET` | `/swagger-ui/index.html` | Interactive Swagger UI for the banking module — full URL: `http://localhost:8081/brite/swagger-ui/index.html` |
| `GET` | `/openapi/banking-openapi.yaml` | The raw OpenAPI 3.0.3 spec that Swagger UI loads |

The spec is hand-written (not generated from the code), so update `src/main/resources/static/openapi/banking-openapi.yaml` whenever a banking controller, DTO, or error mapping changes. Loading the UI needs no `X-Customer-Id` header, but its "Try it out" calls do — click **Authorize** and enter a customer id first.

---

## 🧪 Building & Running

### 0. Use JDK 25
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # macOS; any JDK 25 home works
mvn -v                                              # must say: Java version: 25.x
```
In IntelliJ: *Project Structure → SDKs → add JDK 25*, then set the project SDK and language level to 25.

### 1. Compile the Project
```bash
mvn clean compile
```

### 2. Run Tests
```bash
mvn test
```

### 3. Start the Server
```bash
mvn spring-boot:run
```

---

## 🔍 Sample cURL Requests

```bash
# Customer endpoints need a customer token (demo customer 1 owns CH-0000088291); registration and branch locations don't
CUSTOMER_TOKEN=$(curl -s -X POST http://localhost:8081/brite/v1/api/customers/login -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" -d '{"username":"customer0001","password":"20260001"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")

# Check Actuator Health
curl -s http://localhost:8081/brite/actuator/health

# Kubernetes-style liveness/readiness probes
curl -s http://localhost:8081/brite/actuator/health/liveness
curl -s http://localhost:8081/brite/actuator/health/readiness

# List/Search Accounts (paginated; all filters optional). The seeded CLOSED accounts are
# 26-40 months old (created), so months must be widened to see them (22 CLOSED in total).
curl -s -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  "http://localhost:8081/brite/v1/api/accounts?status=CLOSED&months=48&page=0&size=10&sort=createdDate,desc"

# The 20 seeded SUSPENDED accounts (inside the default window), with flag, start/end and notes
curl -s -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  "http://localhost:8081/brite/v1/api/accounts?status=SUSPENDED&size=25"

# Suspend, update, reactivate: staff only, with an employee token (a manager such as marcus.bell / 20260004).
# A suspended account rejects withdraw/deposit with 400. Customers have no endpoint for these.
MANAGER_TOKEN=$(curl -s -X POST http://localhost:8081/brite/v1/api/staff/login -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" -d '{"username":"marcus.bell","password":"20260004"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
curl -s -X POST -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $MANAGER_TOKEN" -H "Content-Type: application/json" \
  http://localhost:8081/brite/v1/api/staff/accounts/CH-0000010001/suspend \
  -d '{"notes":"Fraud review","endDateTime":"2027-01-31T17:00:00"}'
curl -s -X PATCH -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $MANAGER_TOKEN" -H "Content-Type: application/json" \
  http://localhost:8081/brite/v1/api/staff/accounts/CH-0000010001/suspension -d '{"notes":"Extended after review"}'
curl -s -X POST -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $MANAGER_TOKEN" \
  http://localhost:8081/brite/v1/api/staff/accounts/CH-0000010001/reactivate

# Same endpoint, narrowed to one account (still checked against the resolved date range)
curl -s -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  "http://localhost:8081/brite/v1/api/accounts?accountNumber=CH-0000088291&months=6"

# No explicit dates: defaults to accounts created in the last 18 months (as of today)
curl -s -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $CUSTOMER_TOKEN" "http://localhost:8081/brite/v1/api/accounts"

# Override the default lookback window (last 6 months instead of 18)
curl -s -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $CUSTOMER_TOKEN" "http://localhost:8081/brite/v1/api/accounts?months=6"

# Find ATMs in Texas that accept deposits (office+ATM branches are included), sorted by city
curl -H "X-Customer-Id: cust-1" "http://localhost:8081/brite/v1/api/locations?type=ATM&state=TX&service=ATM_DEPOSIT&sort=city,asc"

# Bank offices with safe-deposit lockers
curl -H "X-Customer-Id: cust-1" "http://localhost:8081/brite/v1/api/locations?type=OFFICE&service=SAFE_DEPOSIT_LOCKER"

# Employee login returns a JWT; send it as Authorization: Bearer on every other staff endpoint
TOKEN=$(curl -s -X POST http://localhost:8081/brite/v1/api/staff/login -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" -d '{"username":"lucas.meyer","password":"20260010"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
# (AREA_MANAGER_TOKEN below comes from the same login with priya.raman / 20260001)
curl -s -X POST "http://localhost:8081/brite/v1/api/staff/accounts/deposit?locationId=1" \
  -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $TOKEN" \
  -d '{"accountNumber":"CH-0000088291","amount":50,"accountType":"CHECKING","depositType":"check","street":"1 Main St","addressLine1":"1 Main St","city":"Austin","state":"TX","zip":"78701"}'
# No/invalid token -> 401; a customer token -> 403; a teller can't suspend (403), a manager (marcus.bell) can.
# Set a login's status (area manager priya.raman):
curl -s -X PUT http://localhost:8081/brite/v1/api/staff/employees/EMP-000010/login-status \
  -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $AREA_MANAGER_TOKEN" \
  -d '{"status":"SUSPENDED","reason":"Under review"}'
# Forgotten password: get the questions, then reset with the answers (no token needed; the user chose the questions while logged in)
curl -s -X POST http://localhost:8081/brite/v1/api/customers/password-reset/questions -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" -d '{"username":"customer0009"}'
curl -s -X POST http://localhost:8081/brite/v1/api/customers/password-reset -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" \
  -d '{"username":"customer0009","newPassword":"13572468","answers":[{"question":"FIRST_CAR","answer":"Honda Civic"},{"question":"FIRST_SCHOOL","answer":"Oak Street"},{"question":"FIRST_TEACHER","answer":"Mrs Patel"}]}'
# Choose the questions first (logged in, current password required)
curl -s -X PUT http://localhost:8081/brite/v1/api/customers/security-questions -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -d '{"currentPassword":"20260001","answers":[{"question":"FIRST_CAR","answer":"Honda Civic"},{"question":"FIRST_SCHOOL","answer":"Oak Street"},{"question":"FIRST_TEACHER","answer":"Mrs Patel"}]}'
# Customer login
curl -s -X POST http://localhost:8081/brite/v1/api/customers/login \
  -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" \
  -d '{"username":"customer0001","password":"20260001"}'

# Look Up a Client Account (banking endpoints require X-Customer-Id, rate-limited to 1000/day)
curl -s -X POST http://localhost:8081/brite/v1/api/accounts/lookup \
  -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -d '{"accountNumber":"CH-0000088291"}'

# Register a New Client Account (response includes an X-BTID header - grep the console
# log for that value to see this request's full trail across controller/service/repository)
curl -s -i -X POST http://localhost:8081/brite/v1/api/accounts/newaccount \
  -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" \
  -d '{
        "firstName":"David","lastName":"Miller","dateOfBirth":"08/19/1994",
        "phoneNumber":"713-555-0142",
        "street":"789 Pine Rd","addressLine1":"789 Pine Rd","city":"Houston","state":"TX","zip":"77001",
        "accountType":"checking"
      }'

# Withdraw From a Client Account
curl -s -X POST http://localhost:8081/brite/v1/api/accounts/withdraw \
  -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -d '{
        "accountNumber":"CH-0000088291","accountType":"CHECKING","withdrawAmount":100.00,
        "firstName":"Alice","lastName":"Smith","street":"123 Main St","city":"Austin",
        "state":"TX","zip":"78701","addressLine1":"Apt 4B"
      }'

# Deposit Into a Client Account
curl -s -X POST http://localhost:8081/brite/v1/api/accounts/deposit \
  -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -d '{
        "accountNumber":"CH-0000088291","amount":250.00,"accountType":"CHECKING","depositType":"cash",
        "firstName":"Alice","lastName":"Smith","street":"123 Main St","city":"Austin",
        "state":"TX","zip":"78701","addressLine1":"Apt 4B"
      }'

# Close a Client Account
curl -s -X POST http://localhost:8081/brite/v1/api/accounts/CH-0000088291/close \
  -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $CUSTOMER_TOKEN"

# Bulk-Close Multiple Accounts (best-effort; invalid ones show up under "failures")
curl -s -X POST http://localhost:8081/brite/v1/api/accounts/close \
  -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -d '{"accountNumbers":["CH-0000010001","SV-0000020001"]}'

# Get a Bank Statement
curl -s "http://localhost:8081/brite/v1/api/accounts/CH-0000088291/statement?beginDate=2026-01-01&endDate=2026-12-31" \
  -H "X-Customer-Id: demo-customer-1" -H "Authorization: Bearer $CUSTOMER_TOKEN"

# Trigger a Fire-and-Forget Async Notification
curl -s "http://localhost:8081/brite/notify?name=Alice" -H "X-Customer-Id: demo-customer-1"

# Call the Resilience4j Circuit Breaker + Retry Demo (run a few times to see variation)
curl -s -X POST http://localhost:8081/brite/v1/payment/process -H "X-Customer-Id: demo-customer-1"

# Sample Item Lookup
curl -s "http://localhost:8081/brite/v1/sample/spl?item=Mac"
```

---

## ✅ Test Suite

Most tests use `@SpringBootTest` + `MockMvc` and require a running MySQL instance (same as the app itself); the banking module's service/gateway/aspect tests are plain unit tests needing neither Spring nor MySQL, and `AccountRepositoryTest` uses `@DataJpaTest` against embedded H2 instead of live MySQL. Run with:
```bash
mvn test
```

| Test Class | Covers |
| :--- | :--- |
| `BankingServicesApplicationTests` | Application context load + actuator health, liveness, and readiness probes |
| `AccountRepositoryTest` | `@DataJpaTest` against embedded H2 (no live MySQL needed — see [Data Model](#-banking-data-model-jpa)) — account creation defaults, ACTIVE/SUSPENDED/CLOSED status lifecycle (suspend/update/reactivate/expire, suspended accounts rejecting withdraw/deposit), withdraw/deposit balance rules, account search/pagination/sorting/date-range filters, conditional accountNumber filter, all as real SQL |
| `AccountSuspensionServiceTest` / `AccountSuspensionExpiryJobTest` | Plain unit tests (no Spring context/MySQL) — the start/end window rules (default start, no future start, end after start and in the future), partial update, reactivation, and the scheduled expiry hook |
| `StaffSuspensionControllerTest` | Standalone MockMvc — the staff suspend/update/reactivate endpoints: binding, `@Valid` (blank/too-long notes → 400), plain-text 400/404 mapping (the customer and portal APIs have no such routes: `CustomerAccessControllerTest` asserts `404`) |
| `AccountStatusDemoSeederTest` | `@DataJpaTest` on H2 — 92 accounts after seeding (22 CLOSED, 20 SUSPENDED), suspended rows carry flag/start/notes and aren't already expired, re-running never duplicates |
| `ClientAccountServiceTest` | Plain unit test (no Spring context/MySQL) — registration age gating, withdraw/deposit input validation, deposit records a transaction with the correct `depositType`, bulk close (all succeed; partial failure doesn't block the rest) |
| `AccountStatusStatementServiceTest` | Plain unit test (no Spring context/MySQL) — account search date-range validation, Account → AccountStatusView mapping (checking vs savings account number, customer name), conditional accountNumber pass-through, default/overridden `months` lookback window |
| `CustomerRateLimiterTest` | Plain unit test (no Spring context/MySQL) — per-customer daily counter: decrements, blocks past the limit, independent per customer |
| `BankingRateLimitFilterTest` | Plain unit test (no Spring context/MySQL) — missing-header rejection, within-limit pass-through + headers, over-limit `429` |
| `BusinessTransactionIdFilterTest` | Plain unit test (no Spring context/MySQL) — btid is in MDC while the chain runs, echoed as `X-BTID`, cleared after (even on exception), and unique per request |
| `ExecutionTimeLoggingAspectTest` | Plain unit test (no Spring context/MySQL) — the `@Around` advice returns the join point's result and propagates exceptions unchanged |
| `EmployeeServiceTest` / `StaffControllerTest` | Plain unit test / standalone MockMvc — each role's privileges, ON_LEAVE/TERMINATED employees, **inactive/suspended/locked/missing logins are rejected**, a rejected employee never reaches the account service, who/where recorded on staff deposits and withdrawals, login-status endpoints per privilege |
| `EmployeeCredentialServiceTest` / `CustomerCredentialServiceTest` | Plain unit tests with real BCrypt — 8-digit password and username rules, hash-only storage, same error for unknown user and wrong password, lock at the limit, admin statuses never overwritten by wrong passwords, expired lock, `changeStatus`, customer transaction check |
| `EmployeeCredentialPersistenceTest` / `CustomerCredentialPersistenceTest` / `EmployeeDataSeederTest` | `@DataJpaTest` on H2 **outside a test transaction** — failed attempts survive the thrown exception, locking, admin status changes, seeded logins verify, seeders |
| `CustomerAuthenticationFilterTest` / `CustomerAccessServiceTest` / `CustomerAccessControllerTest` | Servlet mocks / plain unit / standalone MockMvc — customer token rules (missing/garbage/expired/forged/non-numeric subject → 401, employee token → 403, login no longer ACTIVE → 403, only the two customer-creating endpoints exempt, preflights pass), ownership (own account ok, someone else's 403 on every account and portal endpoint with no service called, bulk close refused as a whole, missing authentication fails closed 401), list and portal home scoped to the caller |
| `PasswordResetPersistenceTest` / `PasswordControllerTest` | `@DataJpaTest` on H2 outside a test transaction / standalone MockMvc — set questions (needs current password; validation; replaced in place, 3 rows), reset with forgiving answers for customers and employees, answers stored as BCrypt hashes, wrong answers counted and locking the reset, one right answer not enough, unknown user = no-questions = wrong answers (same message, stable decoys), bad new password changes nothing, a reset can't undo INACTIVE/SUSPENDED/LOCKED, admin set password; endpoint status mapping (204/401/423/403/400) and fail-closed 401 |
| `LoginControllerTest` / `JwtServiceTest` | Standalone MockMvc / plain unit — login 200 body (token, `no-store`, no password), 401/403/423/400 mapping, no token on failure; JWT claims and 30-min expiry, expired/tampered/foreign-key/wrong-issuer/`alg:none`/unknown-type tokens rejected, short secret refused, random key when no secret |
| `AccountSearchCachingTest` | Plain unit test (no Spring context/MySQL) — reflection check that `listAccountStatuses` carries `@Cacheable` and `registerNewClientAccount`/`closeAccount` carry the matching `@CacheEvict` |

### Banking UI portal BFF

```bash
curl -H "X-Customer-Id: cust-1" -H "Authorization: Bearer $CUSTOMER_TOKEN" "http://localhost:8081/brite/bff/v1/portal/home?state=TX"
curl -H "X-Customer-Id: cust-1" -H "Authorization: Bearer $CUSTOMER_TOKEN" "http://localhost:8081/brite/bff/v1/portal/accounts/CH-0000088291/overview?days=30"
```

The BFF (`org.brite.banking.bff`) composes the existing banking services in process, so the portal makes one call per screen. CORS allows the origins in `banking.portal.allowed-origins` (default the Vite/CRA dev hosts `localhost:5173`/`3000`) for `GET`/`POST`/`PATCH`/`OPTIONS`. Every portal mutation (`withdraw`, `deposit`, `suspend`, `suspension`, `reactivate`, `close`) returns the refreshed account overview (`statement` returns the statement), so the portal redraws from one response.
