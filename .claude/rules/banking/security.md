---
paths:
  - "src/main/java/org/brite/banking/**"
---
# Banking: security and data handling

- **`X-Customer-Id` is a rate-limit key, not authentication.** Don't build authorization on it or describe it as auth. If real auth is added, it goes in the gateway before the rate limiter.
- Validate all input at the edge: Bean Validation on request DTOs; typed enums/dates on query params; sort keys allow-listed before reaching Hibernate.
- SQL: Spring Data derived queries or `Specification` only. No string-concatenated JPQL/SQL.
- PII: names, addresses, `dateOfBirth` and `phoneNumber` never appear in logs, exception messages, or notification bodies beyond what the feature needs. Request logging masks `dateOfBirth`, `phoneNumber` and `password`; extend `mask` for any new sensitive field. The BFF returns phone numbers only masked (`***-***-0101`, last four digits), never in full. Headers are never logged.
- Money math: `BigDecimal` only; reject non-positive amounts; enforce min-balance and cash-deposit caps in the service using `AccountConstraints`.
- Closed accounts: check `AccountStatus` before any balance check on withdraw/deposit.
- Concurrency: rely on `@Version`; don't add read-modify-write outside the facade.
- Secrets (DB password, SMTP/SMS creds) come from environment/`application.yml` placeholders, never hardcoded, never committed, never in tests.
- Error responses must not expose stack traces, SQL, entity names, or other customers' data.
- **Employee and customer logins** (see `employees-and-logins.md`): hash-only storage (BCrypt), 8-digit passwords protected by lockout, same error for unknown user and wrong password, passwords/hashes never logged, returned or put in exceptions, `password` masked by the request logger. Staff endpoints enforce role privileges, and the staff endpoints require the employee JWT (`StaffAuthenticationFilter`; the token is never trusted for permissions or status, which are reloaded on every call). The customer account and portal endpoints require the customer JWT (`CustomerAuthenticationFilter`), re-check the customer's login on every request and keep the caller to their own accounts (`CustomerAccessService`; fail closed when no customer is authenticated; list queries are scoped by `customerId` and the cache key includes it). `X-Customer-Id` is still only a rate-limit key. Registration and branch locations stay open. Suspend, update-suspension and reactivate are staff-only (no customer route exists); a customer can still close their own account. JWT secret only from `BANKING_JWT_SECRET` (never committed), >= 32 chars, HMAC only, no personal data in claims, tokens never logged, login responses `no-store`. `server.error.include-stacktrace: never` stays set (DevTools would otherwise leak traces).
- Don't widen actuator exposure (`management.endpoints.web.exposure.include`) beyond health/probes without review.
