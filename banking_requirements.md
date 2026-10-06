# Banking Services Requirements

This document outlines the functional and technical requirements for the Brite Banking Services application.

## Overview

Banking Services is a Spring Boot 3.5 REST API that provides core banking operations, a portal backend (BFF), employee management, and customer authentication. The service persists data to MySQL and includes features like account suspension, rate limiting, JWT-based authentication, and async notifications.

## Functional Requirements

### 1. Customer Account Management

#### 1.1 Account Registration
- **Requirement:** Customers can register new checking or savings accounts
- **Endpoint:** `POST /v1/api/accounts/newaccount`
- **Input Validation:**
  - First name (required)
  - Last name (required)
  - Date of birth (required, format MM/dd/yyyy)
  - Phone number (required, format XXX-XXX-XXXX)
  - Address (optional)
- **Account Type:** System generates checking (`CH-`) and savings (`SV-`) accounts
- **Initial State:** Accounts start in `ACTIVE` status with zero balance
- **Authentication:** Open endpoint, no token required

#### 1.2 Account Status Lookup
- **Requirement:** Customers can view their own account status and history
- **Endpoint:** `GET /v1/api/accounts?accountNumber=...&status=...&createdFrom=...&createdTo=...&months=...`
- **Features:**
  - Paginated results (default 20 per page)
  - Filterable by account number, status, creation date range
  - Default 18-month lookback window
  - Cacheable for 10 minutes (Caffeine cache)
- **Account Status Values:**
  - `ACTIVE` - Account can transact
  - `SUSPENDED` - Reversible block on transactions
  - `CLOSED` - Permanent closure
  - `INACTIVE` - Manual administrative block (no transactions)
  - `DORMANT` - Manual administrative block (no transactions)
- **Authentication:** Customer token required

#### 1.3 Deposits
- **Requirement:** Customers can deposit funds into their accounts
- **Endpoint:** `POST /v1/api/accounts/deposit`
- **Input:**
  - Account number (required, `CH-` or `SV-` prefixed)
  - Amount (required, positive decimal)
  - Optional: location ID, description
- **Validation Rules:**
  - Account must exist
  - Account must be `ACTIVE` (reject if `SUSPENDED`, `CLOSED`, `INACTIVE`, `DORMANT`)
  - Amount must be positive
- **Transaction Recording:**
  - Creates `AccountTransaction` record with timestamp
  - Records staff identity if employee-initiated
  - Records branch/ATM location if provided
- **Authentication:** Customer token or staff token with `DEPOSIT` privilege

#### 1.4 Withdrawals
- **Requirement:** Customers can withdraw funds from their accounts
- **Endpoint:** `POST /v1/api/accounts/withdraw`
- **Input:**
  - Account number (required)
  - Amount (required, positive decimal)
  - Optional: location ID, withdrawal date, withdrawal form
- **Validation Rules:**
  - Account must exist
  - Account must be `ACTIVE`
  - Amount must be positive
  - Insufficient funds check (minimum balance may apply)
- **Transaction Recording:**
  - Creates `AccountTransaction` record
  - Optionally creates `WithdrawalHistory` record
- **Authentication:** Customer token or staff token with `WITHDRAW` privilege

#### 1.5 Closing Accounts
- **Requirement:** Accounts can be closed (single or bulk)
- **Endpoints:**
  - Single: `POST /v1/api/accounts/{accountNumber}/close`
  - Bulk: `POST /v1/api/accounts/close` (body: list of account numbers)
- **Validation:**
  - Account must exist
  - Cannot close already-closed accounts
  - Can close from `ACTIVE` or `SUSPENDED` state
- **Side Effects:**
  - Sets `accountStatus = CLOSED`
  - Stamps `closedDate = now`
  - Clears any active suspension
- **Authentication:** Customer token or staff token with `CLOSE_ACCOUNT` privilege

### 2. Account Suspension

#### 2.1 Suspension Workflow
- **Requirement:** Staff can suspend and reactivate accounts
- **Staff Privileges Required:** `SUSPEND_ACCOUNT`, `UPDATE_SUSPENSION`, `REACTIVATE_ACCOUNT`

#### 2.2 Suspend Account
- **Endpoint:** `POST /v1/api/staff/accounts/{accountNumber}/suspend`
- **Input:**
  - Notes (required, max 500 chars)
  - Start date/time (optional, defaults to now)
  - End date/time (optional, null = indefinite)
- **Validation Rules:**
  - Start cannot be in the future
  - End must be after start and in the future
  - Account must be `ACTIVE`
- **State Changes:**
  - Sets `accountStatus = SUSPENDED`, `suspended = true`
  - Records `suspendedStart`, `suspendedEnd`, `suspensionNotes`

#### 2.3 Update Suspension
- **Endpoint:** `PATCH /v1/api/staff/accounts/{accountNumber}/suspension`
- **Input:** (at least one required)
  - Notes (max 500 chars)
  - New end date/time (must be future)
- **Validation:**
  - Account must currently be `SUSPENDED`
  - New end must be after original start

#### 2.4 Reactivate Account
- **Endpoint:** `POST /v1/api/staff/accounts/{accountNumber}/reactivate`
- **State Changes:**
  - Sets `accountStatus = ACTIVE`, `suspended = false`
  - Clears `suspendedStart`, `suspendedEnd`, `suspensionNotes`

#### 2.5 Auto-Expiry
- **Requirement:** Suspended accounts automatically reactivate when end date passes
- **Implementation:**
  - Scheduled job runs every 60 seconds (configurable)
  - Queries for expired suspensions
  - Reactivates expired accounts
  - Maintains cache consistency

### 3. Customer Authentication & Logins

#### 3.1 Login System
- **Requirement:** Customers can log in with username and password
- **Endpoint:** `POST /v1/api/customers/login`
- **Input:**
  - Username (lowercase, pattern `^[a-z0-9._-]{3,50}$`)
  - Password (exactly 8 digits)
- **Output:**
  - JWT access token (30-minute expiry, HS256)
  - Token type (Bearer)
  - Expiration time
  - Customer details (id, name only)
- **Validation:**
  - Username/password combination must be valid
  - Login must be in `ACTIVE` status (not `LOCKED`, `SUSPENDED`, `INACTIVE`)
  - Customer must have at least one `ACTIVE` account

#### 3.2 Failed Attempt Lockout
- **Requirement:** Lock login after too many failed attempts
- **Configuration:**
  - Max failed attempts: 5 (configurable: `banking.customer-login.max-failed-attempts`)
  - Lockout duration: 15 minutes (configurable: `banking.customer-login.lockout-minutes`)
- **Behavior:**
  - Wrong password increments counter
  - Unknown username shows same error (security)
  - Counter persists even if attempt throws exception
  - Lock auto-expires after duration

#### 3.3 Customer Login Creation
- **Requirement:** Managers create customer logins
- **Endpoint:** `POST /v1/api/staff/customers/{customerId}/login`
- **Privilege Required:** `MANAGE_CUSTOMER_LOGINS`
- **Input:**
  - Username (required)
  - Password (required, 8 digits, hashed with BCrypt cost 4)
- **Behavior:**
  - Creates `CustomerCredentialEntity` record
  - One login per customer (unique constraint)

#### 3.4 Token Enforcement
- **Requirement:** Customer endpoints require valid token
- **Filter:** `CustomerAuthenticationFilter`
- **Scope:** `/v1/api/accounts/*`, `/bff/v1/portal/*`
- **Exception:** `POST /v1/api/accounts/newaccount` (open registration)
- **Token Validation:**
  - Must be Bearer token format
  - Must be customer token (type = `customer`)
  - Must not be expired
  - Must not predate the login's `passwordChangedAt` timestamp

#### 3.5 Account Ownership Enforcement
- **Requirement:** Customers can only access their own accounts
- **Implementation:** `CustomerAccessService`
- **Rules:**
  - All account operations checked for ownership
  - Requests with wrong account fail with 403
  - Non-existent accounts checked by operation (may return 404)

### 4. Employee Management & Staff Login

#### 4.1 Employee Roles
- **Requirement:** Employees have role-based privileges
- **Roles:**
  - `TELLER`: Basic operations (view, deposit, withdraw, open account)
  - `MANAGER`: Plus suspension, closure, and branch reports
  - `AREA_MANAGER`: All privileges plus employee management
- **Privilege Determination:** Read-only from role on every request (tokens don't authorize)

#### 4.2 Employee Status
- **Requirement:** Only `ACTIVE` employees can work
- **Statuses:** `ACTIVE`, `INACTIVE`, `SUSPENDED`, `LOCKED`
- **Rules:**
  - `ACTIVE` = can work
  - `INACTIVE`/`SUSPENDED` = rejected by `EmployeeService.requireActive`
  - `LOCKED` = automatic after failed login attempts

#### 4.3 Staff Login
- **Endpoint:** `POST /v1/api/staff/login`
- **Input:**
  - Username (same format as customer login)
  - Password (8 digits)
- **Output:**
  - JWT access token (30-minute expiry)
  - Employee details (without PII like email/phone)
  - Branch location (if assigned)

#### 4.4 Token Enforcement (Staff)
- **Requirement:** Staff endpoints require employee token
- **Filter:** `StaffAuthenticationFilter`
- **Scope:** `/v1/api/staff/*`
- **Exception:** `POST /v1/api/staff/login` (open authentication)
- **Validation:**
  - Bearer token format
  - Employee token type (reject customer tokens)
  - Not expired
  - Token doesn't predate password change

#### 4.5 Privilege Enforcement
- **Requirement:** Operations check privilege before execution
- **Implementation:** `EmployeeService.requirePrivilege()`
- **Timing:** On every request, not from token
- **Side Effect:** Immediate effect of demotion/suspension

### 5. Password Management

#### 5.1 Password Change
- **Endpoints:**
  - Customer: `PUT /v1/api/customers/password`
  - Staff: `PUT /v1/api/staff/password`
- **Input:**
  - Current password (required, verified)
  - New password (required, 8 digits)
- **Validation:**
  - Current password must be correct (counted toward lockout if wrong)
  - New password must differ from current
  - Must be exactly 8 digits
  - Login must be `ACTIVE`
- **Side Effects:**
  - Updates `passwordChangedAt` timestamp
  - Clears failed attempt counter
  - Invalidates all older tokens (by `iat` < `passwordChangedAt`)
  - Must log in again

#### 5.2 Security Questions
- **Requirement:** Customers/staff can set security questions for password reset
- **Endpoint:** `PUT /v1/api/{customers|staff}/security-questions`
- **Input:**
  - Current password (required, verified)
  - 3 question/answer pairs
- **Question Types:**
  - `FIRST_CAR`, `FIRST_SCHOOL`, `FIRST_TEACHER`, `FIRST_PET`, `BIRTH_CITY`, `CHILDHOOD_FRIEND`
- **Answer Storage:**
  - BCrypt hashed (cost 4)
  - Normalized (trim, lowercase, collapse whitespace)
  - Stored in `security_answers` table (3 rows per user, slots 1-3)
  - Answers replaced in place (never deleted)

#### 5.3 Password Reset with Questions
- **Endpoints:**
  - `POST /v1/api/{customers|staff}/password-reset/questions` (open, get questions)
  - `POST /v1/api/{customers|staff}/password-reset` (open, reset with answers)
- **Behavior:**
  - Must provide all 3 answers correctly
  - Unknown user returns same error as wrong answers (with dummy BCrypt work)
  - Wrong answers counted per login
  - Lockout: 3 failures / 30 minutes (configurable)
- **Password Reset Rules:**
  - Must answer all 3 questions correctly
  - Login must be `ACTIVE` (reset never undoes administrative blocks)
  - Clears failed attempt counter
  - Invalidates old tokens

#### 5.4 Admin Password Reset
- **Requirement:** Managers can reset logins without questions
- **Endpoints:**
  - Customer: `PUT /v1/api/staff/customers/{id}/password`
  - Staff: `PUT /v1/api/staff/employees/{number}/password`
- **Privilege:** `MANAGE_CUSTOMER_LOGINS` / `MANAGE_EMPLOYEES`
- **Input:** New password (8 digits)
- **Side Effects:**
  - Clears all counters and lock
  - Maintains login status (doesn't undo INACTIVE/SUSPENDED)
  - Invalidates old tokens

### 6. Bank Locations (Branches & ATMs)

#### 6.1 Location Types
- **Requirement:** Support office branches and ATMs
- **Types:**
  - `OFFICE` - Full banking services
  - `ATM` - Withdrawals only
  - `BOTH` - Full services and ATM

#### 6.2 Location Search
- **Endpoint:** `GET /v1/api/locations`
- **Filters:**
  - Type (matches by capability: `OFFICE` → `OFFICE`+`BOTH`, `ATM` → `ATM`+`BOTH`)
  - City, state, zip
  - Services offered
  - Pageable (default 20 per page, sort by name)
- **Sort Keys:** `name`, `locationType`, `city`, `state`

#### 6.3 Location Details
- **Endpoint:** `GET /v1/api/locations/{id}`
- **Returns:** Full location details including hours and services
- **Hours:** 08:00-16:00 wall-clock in America/Chicago timezone (handles DST)

### 7. Bank Statements

#### 7.1 Statement Generation
- **Endpoint:** `POST /v1/api/accounts/{accountNumber}/statement?beginDate=...&endDate=...`
- **Input:** Date range (optional, ISO format `yyyy-MM-dd`)
- **Output:** `BankStatement` with:
  - Account details
  - Filtered transactions (deposits/withdrawals)
  - Totals
- **Privacy Rules:**
  - Customer view: Staff identity cleared (employee fields null)
  - Staff view: Keep employee identity
  - Portal view: Staff identity cleared

#### 7.2 Email/SMS Statements
- **Requirement:** Statements can be emailed/SMS'd
- **Implementation:** Async fire-and-forget via `NotificationService`
- **Configuration:**
  - `notification.email.enabled` (default false)
  - `notification.sms.enabled` (default false)
  - `notification.sms.dailyLimit` (default 100)
- **SMS Rate Limit:** Per-day counter, resets at midnight

### 8. Rate Limiting

#### 8.1 Request Rate Limiting
- **Requirement:** Cap daily requests per customer/employee
- **Filter:** `BankingRateLimitFilter` (runs before request logging)
- **Customer Limits:**
  - Default: 1000 requests/day
  - Per-customer override via `customer_rate_limits` table
  - Counted by customer token or `X-Customer-Id` header (for non-authenticated)
  - Day rollover: Midnight UTC

#### 8.2 Login Rate Limiting
- **Requirement:** Track daily logins per user
- **Implementation:** `CustomerQuotaService.recordLogin` / `EmployeeQuotaService.recordLogin`
- **Tracking:**
  - Count recorded after successful login
  - Failed logins not counted
  - Used for dashboard display (`GET /bff/v1/portal/rate-limit` shows today's logins)

#### 8.3 Rate Limit Enforcement
- **Returns:** 429 Too Many Requests when exceeded
- **Headers:**
  - `X-RateLimit-Limit` - Daily limit
  - `X-RateLimit-Remaining` - Requests left today

### 9. Portal BFF (Backend for Frontend)

#### 9.1 Customer Portal
- **Base Path:** `/bff/v1/portal`
- **Authentication:** Customer token (different from staff token)
- **Endpoints:**

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/home` | Dashboard with accounts and recent activity |
| GET | `/accounts/{n}/overview` | Account details and balance |
| POST | `/accounts/withdraw` | Withdraw funds |
| POST | `/accounts/deposit` | Deposit funds |
| POST | `/accounts/{n}/close` | Close account |
| POST | `/accounts/{n}/statement` | Generate & email statement |
| POST | `/login` | Open: Authenticate |
| PUT | `/password` | Change password |
| PUT | `/security-questions` | Set security questions |
| GET | `/security-questions/catalog` | Open: List available questions |
| POST | `/password-reset/questions` | Open: Get questions for reset |
| POST | `/password-reset` | Open: Reset password |
| GET | `/rate-limit` | View today's usage |

#### 9.2 Staff Portal
- **Base Path:** `/bff/v1/staff`
- **Authentication:** Employee token
- **Endpoints:**

| Method | Path | Purpose | Privilege |
|--------|------|---------|-----------|
| GET | `/accounts/{n}/overview` | View account | VIEW_ACCOUNT |
| POST | `/accounts/open` | Open new account | OPEN_ACCOUNT |
| POST | `/accounts/withdraw` | Withdraw | WITHDRAW |
| POST | `/accounts/deposit` | Deposit | DEPOSIT |
| POST | `/accounts/{n}/close` | Close account | CLOSE_ACCOUNT |
| POST | `/accounts/{n}/suspend` | Suspend account | SUSPEND_ACCOUNT |
| PATCH | `/accounts/{n}/suspension` | Update suspension | UPDATE_SUSPENSION |
| POST | `/accounts/{n}/reactivate` | Reactivate account | REACTIVATE_ACCOUNT |
| GET | `/employees` | List employees | MANAGE_EMPLOYEES |
| GET | `/employees/{n}` | View employee | Own or MANAGE_EMPLOYEES |
| PUT | `/employees/{n}/login-status` | Change login status | MANAGE_EMPLOYEES |
| PUT | `/employees/{n}/password` | Reset employee password | MANAGE_EMPLOYEES |
| GET | `/customers/{id}/overview` | View customer's accounts | VIEW_ACCOUNT |
| POST | `/customers/{id}/login` | Create customer login | MANAGE_CUSTOMER_LOGINS |
| PUT | `/customers/{id}/login-status` | Change login status | MANAGE_CUSTOMER_LOGINS |
| PUT | `/customers/{id}/password` | Reset customer password | MANAGE_CUSTOMER_LOGINS |
| POST | `/login` | Open: Authenticate |
| PUT | `/password` | Change own password |
| PUT | `/security-questions` | Set security questions |
| GET | `/security-questions/catalog` | Open: List questions |
| POST | `/password-reset/questions` | Open: Get questions |
| POST | `/password-reset` | Open: Reset password |
| GET | `/rate-limit` | View own usage |

#### 9.3 BFF Response Shapes
- **AccountOverviewResponse:** Account summary with balance, type, suspension status
- **PortalLoginResponse:** Token, expiry, customer/employee details, dashboard
- **OpenAccountResponse:** New account overview + nearby branches
- **Activity Items:** Recent transactions (hide employee identity)

### 10. API Gateway & Cross-Cutting Concerns

#### 10.1 Business Transaction ID (btid)
- **Implementation:** `BusinessTransactionIdFilter`
- **Behavior:**
  - Generates UUID per request
  - Puts in SLF4J MDC under `btid` key
  - Echoes as `X-BTID` response header
  - All logs include btid automatically

#### 10.2 Request Logging
- **Implementation:** `BankingRequestLoggingFilter`
- **Content:**
  - Logs method, path, query string
  - Includes request body (truncated to 2000 chars)
  - Masks sensitive fields: `password`, `newPassword`, `currentPassword`, `answer`, `phoneNumber`, `dateOfBirth`
- **Level:** INFO

#### 10.3 CORS
- **Customer Portal:** GET/POST/PUT/PATCH/OPTIONS, allows `Authorization` header
- **Staff Portal:** GET/POST/PUT/PATCH/OPTIONS, allows `Authorization` header

### 11. Resilience Patterns

#### 11.1 Payment Processing
- **Endpoint:** `POST /v1/payment/process`
- **Resilience:**
  - `@CircuitBreaker` (breaker name `paymentCircuit`)
  - `@Retry` (max 3 attempts, 100ms backoff, with fallback)
  - Catches `IOException` and `TimeoutException`
- **Fallback:** Returns status `PENDING` instead of failing

#### 11.2 Health Checks
- **Liveness:** `/actuator/health/liveness` (JVM only)
- **Readiness:** `/actuator/health/readiness` (includes database)

## Technical Requirements

### Technology Stack
- **Framework:** Spring Boot 3.5.16
- **Java Version:** 25 (Java bytecode 69)
- **Database:** MySQL 8.x (`db_example`)
- **ORM:** Spring Data JPA 3.5 with Hibernate 6.6
- **Authentication:** JWT (HS256 via jjwt)
- **Password Hashing:** BCrypt (Spring Security Crypto, cost factor 4)
- **Caching:** Spring Cache with Caffeine (10-minute TTL)
- **Async:** Spring `@Async` with `ExecutorServiceBuilder`
- **Scheduling:** Spring `@Scheduled` for expiry job
- **Validation:** Bean Validation (Jakarta)
- **Mapping:** MapStruct 1.6.3
- **Resilience:** Resilience4j

### Database Schema
- **Tables (JPA auto-managed with `ddl-auto: update`):**
  - `accounts` - Account records with balance, status, suspension fields
  - `customers` - Customer personal info
  - `account_transactions` - Deposit/withdraw history with staff tracking
  - `withdrawal_history` - Withdrawal form tracking
  - `bank_locations` - Branch/ATM data
  - `bank_location_services` - Services offered per location (ElementCollection)
  - `bank_employees` - Employee roster with branch/region assignment
  - `bank_employee_credentials` - Employee login (username, BCrypt hash, status)
  - `customer_credentials` - Customer login (username, BCrypt hash, status)
  - `security_answers` - Security Q&A for password reset
  - `customer_rate_limits` - Per-customer daily quotas
  - `employee_rate_limits` - Per-employee daily quotas

### Configuration Properties
**Key banking-specific properties** in `application.yml`:
```yaml
banking:
  jwt:
    secret: ${BANKING_JWT_SECRET}  # >= 32 chars, required for security
    expiration-minutes: 30
  
  customer-login:
    max-failed-attempts: 5
    lockout-minutes: 15
  
  employee-login:
    max-failed-attempts: 5
    lockout-minutes: 15
  
  password-reset:
    max-failed-attempts: 3
    lockout-minutes: 30
  
  rate-limit:
    enabled: true
    customer-header-name: X-Customer-Id  # Required on all banking requests
    requests-per-day: 1000
    employee-requests-per-day: 1000
  
  request-logging:
    enabled: true
    max-payload-length: 2000
  
  suspension:
    expiry-job:
      enabled: true
      interval-ms: 60000  # 60 seconds
  
  portal:
    cors-origins: http://localhost:3000  # React portal
```

### Environment Variables
- `BANKING_JWT_SECRET` - At least 32 characters, never commit (unset = random per restart)
- `JAVA_HOME` - Must point to Java 25 JDK

### Testing Requirements
- **Unit Tests:** JUnit 5 + Mockito (no Spring context for business logic)
- **Integration Tests:** `@SpringBootTest` + MockMvc (requires live MySQL)
- **Repository Tests:** `@DataJpaTest` with H2 (embedded database)
- **Coverage Tool:** JaCoCo (0.8.15+, Java 25 support)

## Non-Functional Requirements

### Performance
- Account search query: Real SQL, paginated, cached 10 minutes
- Concurrent requests: Handled by Spring ThreadPoolExecutor
- Database connections: HikariCP pooling (default 10 connections)

### Security
- **HTTPS:** Recommended for production (enforce via load balancer)
- **No PII in logs:** Mask phone, DOB, passwords
- **No stack traces in responses:** `server.error.include-stacktrace: never`
- **CORS:** Whitelisted origins only
- **Secrets:** `BANKING_JWT_SECRET` never in code or logs

### Availability
- **Graceful Degradation:** Rate limit rejections are fast (no database call)
- **Scheduled Jobs:** Suspension expiry job tolerates lag (≤60 seconds)
- **Health Checks:** Readiness checks database; liveness checks JVM

### Compliance
- **Account Number Format:** `{CH|SV}-{10-digit zero-padded}`
- **Password Format:** Exactly 8 digits
- **Date Format:** ISO `yyyy-MM-dd` for most APIs, `MM/dd/yyyy` for `dateOfBirth` input only

## Related Services

The banking service integrates with separate microservices:

| Service | Port | Context | Purpose |
|---------|------|---------|---------|
| **sampleservice** | 8086 | `/sample` | Simple lookup demo (independent) |
| retailservice | 8082 | `/retail` | Product catalog |
| configservice | 8083 | `/config` | Configuration echo |
| restapiversionservice | 8084 | `/restapi` | API versioning demos |
| eventservice | 8085 | `/event` | Event ingestion |

**Note:** Sample service was moved from bankingservices to https://github.com/mveluru/sampleservice in October 2026.

## Future Enhancements

- [ ] Phone number update endpoint (currently no way to change after registration)
- [ ] Staff-facing statement with employee identity (currently hidden for all)
- [ ] Refresh token support (currently 30-minute expiry only)
- [ ] Real email/SMS sending (currently async mock or disabled)
- [ ] Multi-factor authentication
- [ ] Account transfer between customers
- [ ] Credit line / overdraft support
- [ ] Interest calculation and posting