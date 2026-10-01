---
paths:
  - "src/main/java/org/brite/banking/gateway/**"
---
# Banking layer: gateway filters (`org.brite.banking.gateway`)

Servlet `Filter`s in front of every banking path, registered only through `BankingGatewayConfig` `FilterRegistrationBean`s.

- **Never annotate a filter with `@Component`/`@Service`**: Spring Boot would also auto-register it for `/*`.
- Order (lower runs first): `BusinessTransactionIdFilter` = `HIGHEST_PRECEDENCE`, `BankingRateLimitFilter` = `+1`, `StaffAuthenticationFilter` = `+2` (staff paths only: requires the employee bearer JWT, plain-text `401`/`403`, sets the acting-employee request attribute; the login path is exempt) and `CustomerAuthenticationFilter` = `+2` (`/v1/api/accounts/*` and `/bff/v1/portal/*` only: requires the customer bearer JWT, re-checks the login is ACTIVE, sets the customer-id attribute; exactly `POST /v1/api/accounts/newaccount` and `POST /bff/v1/portal/accounts/open` are exempt; their patterns don't overlap), `BankingRequestLoggingFilter` = `+3`. Keep btid first so rejection logs carry it; authentication sits after the rate limiter so token guessing is rate limited, and before logging so rejected requests aren't body-logged.
- The two authentication filters keep an `OPEN` set of `METHOD path` pairs that need no token (staff: login + the two password-reset calls; customer: `newaccount`, portal `open`, login, the two password-reset calls). Anything not in it, including a new path under their patterns, needs a token by default: a new open endpoint must be added there deliberately, with a test. The portal's open paths (`POST /bff/v1/portal/login`, `GET /bff/v1/portal/security-questions/catalog`, `POST /bff/v1/portal/password-reset[/questions]`) are in the customer filter's `OPEN` set too. `CustomerAuthenticationFilter` covers `/v1/api/accounts/*`, `/v1/api/customers/*` and `/bff/v1/portal/*`; both filters also refuse tokens issued before the password last changed.
- All three use `BankingGatewayConfig.BANKING_URL_PATTERNS` (includes `/v1/api/staff/*` and `/v1/api/customers/*`). Login endpoints sit behind the same per-customer rate limit, which is weak against guessing; the credential lockout is what protects logins. Adding a controller path = adding it there.
- Filters run before `DispatcherServlet`: `@RestControllerAdvice` never sees their errors, so they write status + plain-text body themselves.
- Set MDC `btid` in `try`, `MDC.remove` in `finally` (Tomcat reuses threads).
- Rate limit: header `banking.rate-limit.customer-header-name` (default `X-Customer-Id`) required → `400`; over `requests-per-day` → `429`; always emit `X-RateLimit-Limit`/`X-RateLimit-Remaining`. State in `CustomerRateLimiter` (in-memory, `ConcurrentHashMap<String, AtomicInteger>`, day rollover).
- Request logging: after the chain runs, INFO, truncated to `max-payload-length`; `dateOfBirth`, `phoneNumber` and `password` (string, escaped-string and bare-number forms) masked. Never log headers. New sensitive request fields must be masked in `BankingRequestLoggingFilter.mask`.
- Feature flags come from `banking.*` properties (`@ConditionalOnProperty`/`RateLimitProperties`); defaults are in `application.yml`.
- Swagger UI and `/openapi/**` are intentionally outside the patterns.
