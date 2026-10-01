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
- **Employee and customer logins** (see `employees-and-logins.md`): hash-only storage (BCrypt), 8-digit passwords protected by lockout, same error for unknown user and wrong password, passwords/hashes never logged, returned or put in exceptions, `password` masked by the request logger. Staff endpoints enforce role privileges, but `X-Employee-Number` is a claimed identity, not authentication, until a token exists. `server.error.include-stacktrace: never` stays set (DevTools would otherwise leak traces).
- Don't widen actuator exposure (`management.endpoints.web.exposure.include`) beyond health/probes without review.
