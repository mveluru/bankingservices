---
paths:
  - "src/main/java/org/bee/banking/**"
---
# Banking: security and data handling

- **`X-Customer-Id` is a rate-limit key, not authentication.** Don't build authorization on it or describe it as auth. If real auth is added, it goes in the gateway before the rate limiter.
- Validate all input at the edge: Bean Validation on request DTOs; typed enums/dates on query params; sort keys allow-listed before reaching Hibernate.
- SQL: Spring Data derived queries or `Specification` only. No string-concatenated JPQL/SQL.
- PII: names, addresses and `dateOfBirth` never appear in logs, exception messages, or notification bodies beyond what the feature needs. Request logging masks `dateOfBirth`; extend `mask` for any new sensitive field. Headers are never logged.
- Money math: `BigDecimal` only; reject non-positive amounts; enforce min-balance and cash-deposit caps in the service using `AccountConstraints`.
- Closed accounts: check `AccountStatus` before any balance check on withdraw/deposit.
- Concurrency: rely on `@Version`; don't add read-modify-write outside the facade.
- Secrets (DB password, SMTP/SMS creds) come from environment/`application.yml` placeholders, never hardcoded, never committed, never in tests.
- Error responses must not expose stack traces, SQL, entity names, or other customers' data.
- Don't widen actuator exposure (`management.endpoints.web.exposure.include`) beyond health/probes without review.
