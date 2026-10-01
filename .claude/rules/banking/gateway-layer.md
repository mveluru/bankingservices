---
paths:
  - "src/main/java/org/brite/banking/gateway/**"
---
# Banking layer: gateway filters (`org.brite.banking.gateway`)

Servlet `Filter`s in front of every banking path, registered only through `BankingGatewayConfig` `FilterRegistrationBean`s.

- **Never annotate a filter with `@Component`/`@Service`**: Spring Boot would also auto-register it for `/*`.
- Order (lower runs first): `BusinessTransactionIdFilter` = `HIGHEST_PRECEDENCE`, `BankingRateLimitFilter` = `+1`, `BankingRequestLoggingFilter` = `+2`. Keep btid first so rejection logs carry it.
- All three use `BankingGatewayConfig.BANKING_URL_PATTERNS`. Adding a controller path = adding it there.
- Filters run before `DispatcherServlet`: `@RestControllerAdvice` never sees their errors, so they write status + plain-text body themselves.
- Set MDC `btid` in `try`, `MDC.remove` in `finally` (Tomcat reuses threads).
- Rate limit: header `banking.rate-limit.customer-header-name` (default `X-Customer-Id`) required → `400`; over `requests-per-day` → `429`; always emit `X-RateLimit-Limit`/`X-RateLimit-Remaining`. State in `CustomerRateLimiter` (in-memory, `ConcurrentHashMap<String, AtomicInteger>`, day rollover).
- Request logging: after the chain runs, INFO, truncated to `max-payload-length`, `dateOfBirth` masked. Never log headers. New sensitive request fields must be masked in `BankingRequestLoggingFilter.mask`.
- Feature flags come from `banking.*` properties (`@ConditionalOnProperty`/`RateLimitProperties`); defaults are in `application.yml`.
- Swagger UI and `/openapi/**` are intentionally outside the patterns.
