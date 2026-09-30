# bankingservices (Brite Technology Notifications)

A Spring Boot 3 REST application demonstrating configuration properties binding (`@ConfigurationProperties`), custom REST controllers, async processing, JSON Schema-validated event ingestion, global exception handling, Spring Data JPA, and Spring Boot Actuator monitoring.

---

## 🚀 Features

- **Configuration Management**: Strongly-typed properties bound via `@ConfigurationProperties` for notification options (App, Email, SMS, Retry).
- **Configs API**: Exposes endpoints under `/v1/configs` to query live application, email, and SMS configurations.
- **Product Catalog API**: Exposes endpoints under `/v1/product` to list, look up, and add products (in-memory catalog).
- **Banking APIs**: Client/account lookup, registration, withdrawal, and deposit (`/v1/client`, `/v1/api/accounts`) — accounts, customers, transactions, and withdrawal history are persisted via Spring Data JPA to the same MySQL database as the events module (see below), so data survives app restarts — plus async notification demos (`/notify`, `/report`) backed by `@Async`. Account numbers are always `CH-`/`SV-` (checking/savings) followed by a zero-padded 10-digit number (e.g. `CH-0000088291`), whether seeded or generated on registration. 52 demo accounts (26 checking, 26 savings) are seeded on first startup against an empty database (see [Data Model](#-banking-data-model-jpa) below).
- **Banking API Gateway & Rate Limiter**: Every banking endpoint (`/v1/api/accounts/**`, `/v1/api/locations/**`, `/v1/client/**`, `/v1/payment/**`, `/notify`, `/notify-sms`, `/report`, `/bff/v1/portal/**`) sits behind a `Filter`-based gateway ingress layer that requires an `X-Customer-Id` header and caps each customer to a configurable number of requests per day (`banking.rate-limit`, default 1000/day) — see [Banking API gateway](#-banking-api-gateway--rate-limiter) below. Retail/events/configs endpoints are unaffected.
- **Business Transaction ID (btid) Tracing**: The same gateway stamps every banking request with a unique `btid` (`X-BTID` response header) before it reaches any controller. The id is stored in SLF4J's MDC, so every log line from every layer of that request — controller, service, repository — carries it, letting you grep one request's full log trail with a single id.
- **Account Constraints**: Configurable business rules (`banking.constraints`) enforced on registration/withdrawal/deposit — minimum age to open an account, minimum balance retained after a withdrawal (checking/savings), and a maximum single cash-deposit amount.
- **Bank Statement**: `/v1/api/accounts/{accountNumber}/statement` returns an account's deposit/withdrawal history for a given date range, capped by a configurable maximum range in months.
- **Resilience Demo**: `/v1/payment/process` demonstrates a Resilience4j circuit breaker with jittered exponential-backoff retry around a simulated flaky downstream call.
- **Event Ingestion**: `/api/events` accepts versioned event payloads validated against a JSON Schema (`event-v1.json`) and persisted via Spring Data JPA.
- **API Versioning Demo**: `/apiversion` illustrates URI-, query-param-, header-, and content-negotiation-based API versioning strategies.
- **Actuator Monitoring**: Integrated Spring Boot Actuator exposing health status under `/actuator/health`.
- **Endpoint Execution-Time Logging**: A Spring AOP `@Aspect` (`ExecutionTimeLoggingAspect`, `org.bee.common.logging`) wraps every `@RestController` method app-wide and logs its execution time in milliseconds — no code changes needed per controller.
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

### Configs — `/v1/configs`

| Method | Endpoint Path | Description |
| :--- | :--- | :--- |
| `GET` | `/v1/configs/app-config/values` | Returns application connection pool size and timeout settings |
| `GET` | `/v1/configs/email-config/values` | Returns email notification configuration values |
| `GET` | `/v1/configs/sms-config/values` | Returns SMS notification configuration values |

### Product catalog — `/v1/product`

| Method | Endpoint Path | Description |
| :--- | :--- | :--- |
| `GET` | `/v1/product/allproducts` | Returns all products in the catalog |
| `GET` | `/v1/product/productId/{productId}` | Returns a single product by ID, or `404` if not found |
| `POST` | `/v1/product/addproduct` | Adds a new product to the catalog and returns it |
| `GET` | `/v1/product/productmessage` | Triggers an internal product/user lookup and returns a confirmation message |

### Banking — clients, accounts & notifications

> Every endpoint below requires an `X-Customer-Id` header and is subject to the per-customer daily rate limit — see [Banking API gateway & rate limiter](#banking-api-gateway--rate-limiter).

| Method | Endpoint Path | Description |
| :--- | :--- | :--- |
| `GET` | `/v1/client/name` | Returns a sample customer record |
| `GET` | `/v1/api/accounts?accountNumber=&status=&createdFrom=&createdTo=&closedFrom=&closedTo=&months=&page=&size=&sort=` | Retrieves account ids/details within a createdDate/closedDate range, paginated (backed by a real JPA `Specification` query, cached for 10 minutes). All filters optional. **Default lookback**: if neither `createdFrom` nor `createdTo` is given, defaults to "as of today minus `months` months" (18 months if `months` is also omitted); supplying either explicit created-date bound disables this default and `months` is ignored (`400` if `months` isn't positive). **Conditional lookup**: if `accountNumber` is given, only that account is returned (still subject to the other filters — an out-of-range match yields an empty page, not a bypass); if omitted/null, every matching account is returned. Other filters: `status` (`ACTIVE`/`CLOSED`), `closedFrom`/`closedTo` (inclusive `yyyy-MM-dd` range), standard Spring Data `page`/`size`/`sort` (sortable by `createdDate`, `closedDate`, `accountStatus`, `accountNumber`; default `size=20`, sorted by `createdDate` ascending). Returns a Spring Data `Page<AccountStatusView>` envelope (`content`, `totalElements`, `totalPages`, etc) — each row is flattened to `accountNumber`, `accountType`, `accountStatus`, `createdDate`, `closedDate`, `firstName`, `lastName`, not the full nested `Account`/`Customer`. `400` if a `*From` date is after its `*To` date or an unsupported `sort` property is given |
| `POST` | `/v1/api/accounts/lookup` | Looks up an account by account number; `404` if not found |
| `POST` | `/v1/api/accounts/newaccount` | Registers a new customer + account. `phoneNumber` is required and must be `###-###-####` (`400` otherwise); it is stored on the customer and returned in the `Account` response. The request logger masks it (`***`) like `dateOfBirth` |
| `POST` | `/v1/api/accounts/withdraw` | Withdraws funds from a checking/savings account; `400` on insufficient funds, mismatched account type, a `CLOSED` or `SUSPENDED` account, `404` if the account doesn't exist |
| `POST` | `/v1/api/accounts/deposit` | Deposits funds into a checking/savings account; `400` on invalid amount/deposit type, mismatched account type, a cash amount over the configured maximum, or a `CLOSED` or `SUSPENDED` account, `404` if the account doesn't exist |
| `POST` | `/v1/api/accounts/{accountNumber}/close` | Closes a checking/savings account (status `ACTIVE` or `SUSPENDED` → `CLOSED`, stamps `closedDate`, clears any suspension); `400` if already closed, `404` if the account doesn't exist |
| `POST` | `/v1/api/accounts/{accountNumber}/suspend` | Suspends an `ACTIVE` account. Body: `notes` (required, max 500), optional `startDateTime` (ISO local date-time; defaults to now, not in the future) and `endDateTime` (omit = indefinite; must be after the start and in the future). **A suspended account rejects every withdraw and deposit (`400`) until it is reactivated** or `endDateTime` passes (the expiry job, every 60 s, reactivates it). `400` if closed, already suspended, or the window is invalid; `404` unknown account |
| `PATCH` | `/v1/api/accounts/{accountNumber}/suspension` | Updates a current suspension: `notes` and/or `endDateTime` (only supplied fields change, at least one required; new end must be in the future and after the stored start). `400` if the account isn't suspended |
| `POST` | `/v1/api/accounts/{accountNumber}/reactivate` | `SUSPENDED` → `ACTIVE`; clears the suspension flag, dates and notes so the account can transact again. `400` if not suspended |
| `POST` | `/v1/api/accounts/close` | Bulk-closes multiple accounts in one call (body: `{"accountNumbers": [...]}`). Best-effort — an invalid/already-closed account number doesn't block the others; the `200` response carries `closedAccounts` (the ones that succeeded) and `failures` (`accountNumber` + `reason` for the rest). `400` if `accountNumbers` is empty/missing |
| `GET` | `/v1/api/accounts/{accountNumber}/statement?beginDate=yyyy-MM-dd&endDate=yyyy-MM-dd` | Returns a bank statement (deposit/withdrawal history) for the account in the given range; each transaction includes `depositType` (`"cash"`/`"check"` for deposits, `null` for withdrawals); `400` if the range exceeds the configured maximum months, `404` if the account doesn't exist |
| `GET` | `/v1/api/locations?type=&city=&state=&zip=&service=&page=&size=&sort=` | Searches bank offices/ATMs, paginated (real JPA `Specification` query, not cached). All filters optional and AND'd; `city`/`state`/`zip` are case-insensitive exact matches. `type` matches by capability: `OFFICE` returns offices **and** office+ATM branches, `ATM` returns ATMs **and** office+ATM branches, `BOTH` only the branches with both. `service` is one of `BANKING`, `SAFE_DEPOSIT_LOCKER`, `LOANS_MORTGAGES`, `NOTARY`, `WIRE_TRANSFER`, `FOREIGN_EXCHANGE`, `ATM_CASH_WITHDRAWAL`, `ATM_DEPOSIT`. Sortable by `name` (default), `locationType`, `city`, `state`; default `size=20`. Each row has `name`, `bankAddress`, `locationType`, office `opensAt`/`closesAt` (`08:00:00`/`16:00:00`, wall clock in `timeZone` `America/Chicago`), office `phoneNumber` and `services` — hours and phone are `null` for ATM-only rows. `400` for an unsupported `sort` property or unknown `type`/`service` |
| `GET` | `/v1/api/locations/{id}` | Returns one bank office/ATM by id (same shape as a search row); `404` if no location has that id, `400` if `id` isn't numeric |
| `GET` | `/bff/v1/portal/home?state=` | **BFF for the banking UI portal.** One call for the home screen: the newest `ACTIVE` and `SUSPENDED` accounts merged (max 5, closed omitted, no balances; each row has `suspended`/`suspendedUntil`) plus branches/ATMs (max 5, sorted by name; filtered by `state` if given). Returns `totalActiveAccounts`, `totalSuspendedAccounts`, `accounts`, `nearbyLocations` |
| `GET` | `/bff/v1/portal/accounts/{accountNumber}/overview?days=` | Account detail: balance plus transactions from the last `days` days (default 30, `1`–`90`), newest first, max 20. Returns `maskedPhoneNumber` (`***-***-0101`, last four digits only; the BFF never returns a full number). Read-only (no email/SMS, unlike the statement endpoint). `404` unknown account, `400` bad `days` |
| `POST` | `/bff/v1/portal/accounts/open` | Same body (incl. required `phoneNumber`), rules and side effects as `POST /v1/api/accounts/newaccount`, plus `nearbyLocations` in the customer's state. `201` |
| `POST` | `/bff/v1/portal/accounts/withdraw` | Same body/rules/side effects as `POST /v1/api/accounts/withdraw`, but returns the **refreshed account overview** (balance, suspension state, recent activity). `400` (plain text) if the account is `CLOSED` or `SUSPENDED`, on insufficient funds, etc. |
| `POST` | `/bff/v1/portal/accounts/deposit` | Same as `POST /v1/api/accounts/deposit`, returning the refreshed overview; `400` if `CLOSED` or `SUSPENDED` |
| `POST` | `/bff/v1/portal/accounts/{accountNumber}/suspend` | Same body/rules as the banking suspend endpoint; returns the refreshed overview (`suspended: true`, `suspendedUntil`; notes are not included) |
| `PATCH` | `/bff/v1/portal/accounts/{accountNumber}/suspension` | Updates `notes` and/or `endDateTime` of a current suspension; returns the refreshed overview |
| `POST` | `/bff/v1/portal/accounts/{accountNumber}/close` | Same rules as the banking close endpoint (irreversible); returns the refreshed overview (`CLOSED`, `closedDate`). `400` if already closed |
| `POST` | `/bff/v1/portal/accounts/{accountNumber}/statement?beginDate=&endDate=` | Same rules and result as the banking statement endpoint, **including its email/SMS notification**; a `POST` because portal reads are side-effect free. Returns the `BankStatement` |
| `POST` | `/bff/v1/portal/accounts/{accountNumber}/reactivate` | Lifts the suspension; returns the refreshed overview (`ACTIVE`, `suspended: false`) |
| `GET` | `/notify?name={name}` | Fire-and-forget async email notification demo |
| `GET` | `/report` | Async task that returns a completed report string |

### Banking API gateway & rate limiter

`BankingRateLimitFilter` (`org.bee.banking.gateway`) is a servlet `Filter` registered only for the banking module's URL patterns (`/v1/api/accounts/*`, `/v1/api/locations`, `/v1/api/locations/*`, `/v1/client/*`, `/v1/payment/*`, `/notify`, `/notify-sms`, `/report`, `/bff/v1/portal/*`) — it runs before `DispatcherServlet`, so rejected requests never reach a controller. It acts as a lightweight API-gateway ingress layer with two responsibilities:

1. **Customer identification** — every request must carry the header configured by `banking.rate-limit.customer-header-name` (default `X-Customer-Id`). Missing/blank header → `400` with a plain-text explanation.
2. **Per-customer daily rate limit** — each customer ID is capped at `banking.rate-limit.requests-per-day` (default **1000**) requests per calendar day, tracked in-memory and reset at midnight. Exceeding it → `429 Too Many Requests`.

Every response that reaches the filter (allowed or rejected) carries `X-RateLimit-Limit` and `X-RateLimit-Remaining` headers. Set `banking.rate-limit.enabled: false` to bypass the whole gateway (e.g. for local scripting). The counters themselves are a single-instance, in-memory `ConcurrentHashMap` (unlike account/customer/transaction data, which is now persisted via JPA — see [Data Model](#-banking-data-model-jpa)) — not a distributed rate limiter — and the customer ID is a caller-supplied header rather than an authenticated principal, since the app has no auth layer.

A second filter, `BusinessTransactionIdFilter`, is registered on the same URL patterns but runs *first* (ahead of the rate limiter), so it stamps a unique business transaction id (`btid`, a UUID) onto every banking request before anything else touches it — including requests the rate limiter goes on to reject. The `btid` is put into SLF4J's MDC and echoed back as the `X-BTID` response header; every log line for that request, in every layer (controller, service, repository), automatically includes `[btid=...]` via the `logging.pattern.console` entry in `application.yml` — no parameter threading required. It's cleared from MDC in a `finally` block after each request so it never leaks onto Tomcat's reused worker threads. Logs outside any request (startup, scheduled tasks) show `[btid=-]`.

### Banking data model (JPA)

Banking used to be a `ConcurrentHashMap`-backed mock store; it's now backed by real JPA entities persisted to the same MySQL database (`db_example`) as the events module. The service/controller layers still use the same `Account`/`Customer`/`Address`/`AccountTransaction`/`WithdrawalForm` domain objects as before — the entity ↔ domain mapping is entirely internal to the repository classes, so no other code changed for this migration.

| Entity (`org.bee.banking.entity`) | Table | Notes |
| :--- | :--- | :--- |
| `AccountEntity` | `accounts` | One row per real account (checking **or** savings) — not one row per customer pairing. `@Version` column for optimistic locking on concurrent withdraw/deposit. Status is `ACTIVE`, `SUSPENDED` or `CLOSED`; suspension is stored in `suspended` (flag), `suspended_start`/`suspended_end` (datetimes, end null = indefinite) and `suspension_notes`. |
| `CustomerEntity` | `customers` | `@ManyToOne` from `AccountEntity`, cascades on save. Holds name, date of birth, `phoneNumber` (`###-###-####`, nullable for customers registered before the field existed) and the embedded address. |
| `AddressEmbeddable` | — | `@Embeddable`, inlined as columns on `CustomerEntity`/`WithdrawalHistoryEntity` — no separate table. |
| `AccountTransactionEntity` | `account_transactions` | One row per deposit/withdrawal, including `depositType`. |
| `WithdrawalHistoryEntity` | `withdrawal_history` | Separate withdrawal-specific history (write-only, nothing reads it back — same as before the migration). |
| `BankLocationEntity` | `bank_locations`, `bank_location_services` | A bank office and/or ATM: `locationType` (`OFFICE`/`ATM`/`BOTH`), address (`BankAddressEmbeddable`, inlined), office hours 08:00–16:00 in `America/Chicago` (Central time), office phone, and the set of `BankOperationServices` it serves (`BANKING`, `SAFE_DEPOSIT_LOCKER`, `LOANS_MORTGAGES`, `NOTARY`, `WIRE_TRANSFER`, `FOREIGN_EXCHANGE`, `ATM_CASH_WITHDRAWAL`, `ATM_DEPOSIT`) in the second table. ATM-only rows have no hours or phone. Domain shapes: `BankLocations`, `BankAddress`. |

Raw Spring Data repositories live in `org.bee.banking.repository.jpa` (`AccountJpaRepository` — extends `JpaSpecificationExecutor` for the dynamic account-search filtering — plus `CustomerJpaRepository`, `AccountTransactionJpaRepository`, `WithdrawalHistoryJpaRepository`); application code never touches them directly. `AccountRepository`/`TransactionRepository`/`WithdrawalRepository` (same names/packages as before) wrap them and keep their old public method signatures.

`BankLocationDataSeeder` likewise seeds 20 demo bank locations (8 office, 6 ATM, 6 office+ATM across Central-time cities) when `bank_locations` is empty. `AccountDataSeeder` seeds the 52 demo accounts on first startup, but only if the `accounts` table is empty. `AccountStatusDemoSeeder` then adds 40 more — 20 `CLOSED` (`CH-0000030001..30010`, `SV-0000040001..40010`) and 20 `SUSPENDED` (`CH-0000050001..50010`, `SV-0000060001..60010`, 14 with an end date and 6 indefinite, all with notes) — for any of those numbers that don't exist yet, so an existing database gets them on the next start (92 accounts in total). Every seeded customer has a phone number, `512-555-0001`…`0092` (customer N = `512-555-000N`); an older database gets them from `db/data/06_backfill_customer_phones.sql`. The base seeder only runs on an empty table because data now persists across restarts, so unconditional reseeding would create duplicates every time the app starts. Account numbers are still generated the same way as before (zero-padded 10-digit `CH-`/`SV-` numbers, starting from a `10001` counter), so previously-documented account numbers remain valid.

### Resilience demo — `/v1/payment`

| Method | Endpoint Path | Description |
| :--- | :--- | :--- |
| `POST` | `/v1/payment/process` | Calls a simulated flaky bank service (40% failure rate) through a Resilience4j circuit breaker + jittered exponential-backoff retry; returns a fallback message once the breaker opens |

### Event ingestion — `/api/events`

| Method | Endpoint Path | Description |
| :--- | :--- | :--- |
| `POST` | `/api/events` | Validates a `v1` event payload against JSON Schema, then persists it |

### Sample lookup — `/v1/sample`

| Method | Endpoint Path | Description |
| :--- | :--- | :--- |
| `GET` | `/v1/sample/spl?item={item}` | Looks up a sample item count by name (e.g. `Mac`, `Dell`, `IBM`) |

### API versioning demo — `/apiversion`

| Method | Endpoint Path | Description |
| :--- | :--- | :--- |
| `GET` | `/apiversion/v1/api` | Versioning via URI path (v1) |
| `GET` | `/apiversion/v2/api` | Versioning via URI path (v2) |
| `GET` | `/apiversion/api?v1` | Versioning via request parameter (v1) |
| `GET` | `/apiversion/api?v2` | Versioning via request parameter (v2) |
| `GET` | `/apiversion/api` (header `X-API-VERSION: 1`) | Versioning via custom header (v1) |
| `GET` | `/apiversion/api` (header `X-API-VERSION: 2`) | Versioning via custom header (v2) |
| `GET` | `/apiversion/api` (`Accept: application/vnd.company.app-v1+json`) | Versioning via content negotiation (v1) |
| `GET` | `/apiversion/api` (`Accept: application/vnd.company.app-v2+json`) | Versioning via content negotiation (v2) |

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
# Get Application Config
curl -s http://localhost:8081/brite/v1/configs/app-config/values

# Get Email Config
curl -s http://localhost:8081/brite/v1/configs/email-config/values

# Get SMS Config
curl -s http://localhost:8081/brite/v1/configs/sms-config/values

# Check Actuator Health
curl -s http://localhost:8081/brite/actuator/health

# Kubernetes-style liveness/readiness probes
curl -s http://localhost:8081/brite/actuator/health/liveness
curl -s http://localhost:8081/brite/actuator/health/readiness

# Get All Products
curl -s http://localhost:8081/brite/v1/product/allproducts

# Get Product By ID
curl -s http://localhost:8081/brite/v1/product/productId/101

# Add a Product
curl -s -X POST http://localhost:8081/brite/v1/product/addproduct \
  -H "Content-Type: application/json" \
  -d '{"productId":"200","productName":"Test Widget","quantity":"5","price":42.5}'

# List/Search Accounts (paginated; all filters optional). The seeded CLOSED accounts are
# 26-40 months old (created), so months must be widened to see them (22 CLOSED in total).
curl -s -H "X-Customer-Id: demo-customer-1" \
  "http://localhost:8081/brite/v1/api/accounts?status=CLOSED&months=48&page=0&size=10&sort=createdDate,desc"

# The 20 seeded SUSPENDED accounts (inside the default window), with flag, start/end and notes
curl -s -H "X-Customer-Id: demo-customer-1" \
  "http://localhost:8081/brite/v1/api/accounts?status=SUSPENDED&size=25"

# Suspend, update, reactivate (a suspended account rejects withdraw/deposit with 400)
curl -s -X POST -H "X-Customer-Id: demo-customer-1" -H "Content-Type: application/json" \
  http://localhost:8081/brite/v1/api/accounts/CH-0000010001/suspend \
  -d '{"notes":"Fraud review","endDateTime":"2027-01-31T17:00:00"}'
curl -s -X PATCH -H "X-Customer-Id: demo-customer-1" -H "Content-Type: application/json" \
  http://localhost:8081/brite/v1/api/accounts/CH-0000010001/suspension -d '{"notes":"Extended after review"}'
curl -s -X POST -H "X-Customer-Id: demo-customer-1" \
  http://localhost:8081/brite/v1/api/accounts/CH-0000010001/reactivate

# Same endpoint, narrowed to one account (still checked against the resolved date range)
curl -s -H "X-Customer-Id: demo-customer-1" \
  "http://localhost:8081/brite/v1/api/accounts?accountNumber=CH-0000088291&months=6"

# No explicit dates: defaults to accounts created in the last 18 months (as of today)
curl -s -H "X-Customer-Id: demo-customer-1" "http://localhost:8081/brite/v1/api/accounts"

# Override the default lookback window (last 6 months instead of 18)
curl -s -H "X-Customer-Id: demo-customer-1" "http://localhost:8081/brite/v1/api/accounts?months=6"

# Find ATMs in Texas that accept deposits (office+ATM branches are included), sorted by city
curl -H "X-Customer-Id: cust-1" "http://localhost:8081/brite/v1/api/locations?type=ATM&state=TX&service=ATM_DEPOSIT&sort=city,asc"

# Bank offices with safe-deposit lockers
curl -H "X-Customer-Id: cust-1" "http://localhost:8081/brite/v1/api/locations?type=OFFICE&service=SAFE_DEPOSIT_LOCKER"

# Look Up a Client Account (banking endpoints require X-Customer-Id, rate-limited to 1000/day)
curl -s -X POST http://localhost:8081/brite/v1/api/accounts/lookup \
  -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" \
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
  -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" \
  -d '{
        "accountNumber":"CH-0000088291","accountType":"CHECKING","withdrawAmount":100.00,
        "firstName":"Alice","lastName":"Smith","street":"123 Main St","city":"Austin",
        "state":"TX","zip":"78701","addressLine1":"Apt 4B"
      }'

# Deposit Into a Client Account
curl -s -X POST http://localhost:8081/brite/v1/api/accounts/deposit \
  -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" \
  -d '{
        "accountNumber":"CH-0000088291","amount":250.00,"accountType":"CHECKING","depositType":"cash",
        "firstName":"Alice","lastName":"Smith","street":"123 Main St","city":"Austin",
        "state":"TX","zip":"78701","addressLine1":"Apt 4B"
      }'

# Close a Client Account
curl -s -X POST http://localhost:8081/brite/v1/api/accounts/CH-0000088291/close \
  -H "X-Customer-Id: demo-customer-1"

# Bulk-Close Multiple Accounts (best-effort; invalid ones show up under "failures")
curl -s -X POST http://localhost:8081/brite/v1/api/accounts/close \
  -H "Content-Type: application/json" -H "X-Customer-Id: demo-customer-1" \
  -d '{"accountNumbers":["CH-0000010001","SV-0000020001"]}'

# Get a Bank Statement
curl -s "http://localhost:8081/brite/v1/api/accounts/CH-0000088291/statement?beginDate=2026-01-01&endDate=2026-12-31" \
  -H "X-Customer-Id: demo-customer-1"

# Trigger a Fire-and-Forget Async Notification
curl -s "http://localhost:8081/brite/notify?name=Alice" -H "X-Customer-Id: demo-customer-1"

# Call the Resilience4j Circuit Breaker + Retry Demo (run a few times to see variation)
curl -s -X POST http://localhost:8081/brite/v1/payment/process -H "X-Customer-Id: demo-customer-1"

# Submit a v1 Event
curl -s -X POST http://localhost:8081/brite/api/events \
  -H "Content-Type: application/json" \
  -d '{
        "version":"v1","eventId":"evt_123abc","timestamp":"2026-09-17T10:00:00Z",
        "payload":{"userId":"u123","email":"user@example.com"}
      }'

# API Versioning via URI Path
curl -s http://localhost:8081/brite/apiversion/v1/api

# Sample Item Lookup
curl -s "http://localhost:8081/brite/v1/sample/spl?item=Mac"
```

Sample JSON Response (`/app-config/values`):
```json
{
  "connectionPoolSize": "10",
  "timeoutInSeconds": "1"
}
```

---

## ✅ Test Suite

Most tests use `@SpringBootTest` + `MockMvc` and require a running MySQL instance (same as the app itself); the banking module's service/gateway/aspect tests are plain unit tests needing neither Spring nor MySQL, and `AccountRepositoryTest` uses `@DataJpaTest` against embedded H2 instead of live MySQL. Run with:
```bash
mvn test
```

| Test Class | Covers |
| :--- | :--- |
| `ProductControllerTest` | `/v1/product` endpoints — list, get by ID (found + `404` not-found), add, product message |
| `BriteConfigValuesControllerTest` | `/v1/configs` config endpoints — app, email, SMS |
| `BankingServicesApplicationTests` | Application context load + actuator health, liveness, and readiness probes |
| `AccountRepositoryTest` | `@DataJpaTest` against embedded H2 (no live MySQL needed — see [Data Model](#-banking-data-model-jpa)) — account creation defaults, ACTIVE/SUSPENDED/CLOSED status lifecycle (suspend/update/reactivate/expire, suspended accounts rejecting withdraw/deposit), withdraw/deposit balance rules, account search/pagination/sorting/date-range filters, conditional accountNumber filter, all as real SQL |
| `AccountSuspensionServiceTest` / `AccountSuspensionExpiryJobTest` | Plain unit tests (no Spring context/MySQL) — the start/end window rules (default start, no future start, end after start and in the future), partial update, reactivation, and the scheduled expiry hook |
| `AccountSuspensionControllerTest` | Standalone MockMvc — suspend/update/reactivate binding, `@Valid` (blank/too-long notes → 400), plain-text 400/404 mapping |
| `AccountStatusDemoSeederTest` | `@DataJpaTest` on H2 — 92 accounts after seeding (22 CLOSED, 20 SUSPENDED), suspended rows carry flag/start/notes and aren't already expired, re-running never duplicates |
| `ClientAccountServiceTest` | Plain unit test (no Spring context/MySQL) — registration age gating, withdraw/deposit input validation, deposit records a transaction with the correct `depositType`, bulk close (all succeed; partial failure doesn't block the rest) |
| `AccountStatusStatementServiceTest` | Plain unit test (no Spring context/MySQL) — account search date-range validation, Account → AccountStatusView mapping (checking vs savings account number, customer name), conditional accountNumber pass-through, default/overridden `months` lookback window |
| `CustomerRateLimiterTest` | Plain unit test (no Spring context/MySQL) — per-customer daily counter: decrements, blocks past the limit, independent per customer |
| `BankingRateLimitFilterTest` | Plain unit test (no Spring context/MySQL) — missing-header rejection, within-limit pass-through + headers, over-limit `429` |
| `BusinessTransactionIdFilterTest` | Plain unit test (no Spring context/MySQL) — btid is in MDC while the chain runs, echoed as `X-BTID`, cleared after (even on exception), and unique per request |
| `ExecutionTimeLoggingAspectTest` | Plain unit test (no Spring context/MySQL) — the `@Around` advice returns the join point's result and propagates exceptions unchanged |
| `AccountSearchCachingTest` | Plain unit test (no Spring context/MySQL) — reflection check that `listAccountStatuses` carries `@Cacheable` and `registerNewClientAccount`/`closeAccount` carry the matching `@CacheEvict` |

### Banking UI portal BFF

```bash
curl -H "X-Customer-Id: cust-1" "http://localhost:8081/brite/bff/v1/portal/home?state=TX"
curl -H "X-Customer-Id: cust-1" "http://localhost:8081/brite/bff/v1/portal/accounts/CH-0000088291/overview?days=30"
```

The BFF (`org.bee.banking.bff`) composes the existing banking services in process, so the portal makes one call per screen. CORS allows the origins in `banking.portal.allowed-origins` (default the Vite/CRA dev hosts `localhost:5173`/`3000`) for `GET`/`POST`/`PATCH`/`OPTIONS`. Every portal mutation (`withdraw`, `deposit`, `suspend`, `suspension`, `reactivate`, `close`) returns the refreshed account overview (`statement` returns the statement), so the portal redraws from one response.
