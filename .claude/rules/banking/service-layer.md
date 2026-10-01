---
paths:
  - "src/main/java/org/brite/banking/service/**"
---
# Banking layer: services (`org.brite.banking.service`)

- `@Service @Slf4j @RequiredArgsConstructor`; collaborators are `private final` facades/services/`AccountConstraints`.
- All business rules live here: minimum age, minimum balance, max cash deposit, statement date range, account-type from prefix, date-range and `months` validation.
- Validate inputs first, then call the repository, then side effects (notifications). Reject with a typed exception carrying a `BankingMessages` string.
- Services depend on repository *facades*, never on `repository.jpa` or entities.
- Suspension lifecycle (suspend / update / reactivate / expire) is `AccountSuspensionService`; `AccountSuspensionExpiryJob` is the `@Scheduled` trigger (gated by `banking.suspension.expiry-job.enabled`) and must call the service bean, never the repository, so `@CacheEvict` runs.
- Employee/login services: `EmployeeService` (privilege + ACTIVE-login check, profile reads), `StaffAccountService`/`StaffLoginService` (check, then delegate; no rules of their own), `EmployeeCredentialService`/`CustomerCredentialService` (`createLogin`, `verify`, `changeStatus`, customer `requireActiveLoginIfPresent`), `LoginSupport` (shared password/lockout rules), `LoginService` (verify, then issue the JWT; no token on failure) and `JwtService` (sign/parse HS256 tokens; injectable `Clock` for tests). `verify` must keep `noRollbackFor`. A privilege check always runs before the delegated call. See `employees-and-logins.md`.
- Separate concerns stay separate: `AccountStatusStatementService` owns the account-status view/projection; `ClientAccountService` owns account lifecycle; `BankStatementService` owns statements; `LocationBasedOperationService` owns location lookup. New reports/views go to the matching service, not into a repository.
- Transactions: put `@Transactional` on repository facade methods (`readOnly = true` for reads). Add it to a service only when one operation spans several repository calls that must be atomic.

## Caching
- Only `AccountStatusStatementService.listAccountStatuses` is cached (`ACCOUNT_SEARCH_CACHE`, Caffeine, 10 min TTL in `application.yml`).
- Mutations that change an `AccountStatusView` field carry `@CacheEvict(cacheNames = ACCOUNT_SEARCH_CACHE, allEntries = true)`.
- Never call an `@CacheEvict`/`@Cacheable`/`@Async`/`@Retry` method through `this`: the proxy is bypassed. Call the repository directly (see bulk close) or move the method to another bean.
- Do not cache anything exposing balance without evicting on withdraw/deposit.

## Notifications
- `NotificationService.sendEmail/sendSms` are `@Async` fire-and-forget, gated by `notification.*` config. Called from register, withdraw, and statement generation; deposit does not notify. Never block a request on them or let their failure fail the transaction.

## Resilience
- `PaymentService.processPayment`: `@Retry` above `@CircuitBreaker`; only `@Retry` has `fallbackMethod`. Keep that ordering.

## Bulk operations
- Best effort, per-item failures collected in the result body, HTTP `200`, cache evicted once for the batch.
