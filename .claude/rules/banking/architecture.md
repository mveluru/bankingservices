---
paths:
  - "src/main/java/org/brite/banking/**"
  - "src/test/java/org/brite/banking/**"
---
# Banking module: architecture

Vertical slice under `org.brite.banking`. Request flow:

```
HTTP → gateway filters (btid → rate limit → request log)
     → contoller (REST, validation, no logic)      or  bff.controller (UI-shaped, one call per screen)
     → service (business rules, cache, notifications)
     → repository facade (entity ↔ domain mapping, Specifications)
     → repository.jpa (raw Spring Data, never used outside the facade)
     → MySQL
```

| Package | Role | May depend on |
|---|---|---|
| `contoller` (sic) | HTTP mapping only | `service`, `domain`, `request` |
| `service` | Business logic, `@Cacheable`/`@CacheEvict`, notification calls | `repository`, `domain`, `exception`, `messages`, `rules`, `component` |
| `repository` | Facades: entity ↔ domain mapping, `Specification` queries, account-number generation | `repository.jpa`, `entity`, `domain`, `messages` |
| `repository.jpa` | Raw `JpaRepository` interfaces | `entity` |
| `entity` | JPA persistence shape | `domain` enums only |
| `domain` | Objects the service/controller layers use, incl. enums and view DTOs | nothing in banking except `validtors` |
| `request` | Inbound bodies + Bean Validation | `messages`, `domain.validtors` |
| `component` | MapStruct mappers, `BankClient` | `domain`, `request` |
| `exception` | Typed exceptions + `BankingExceptionHandler` | `messages` |
| `messages` | `BankingMessages` constants | nothing |
| `rules` | `@ConfigurationProperties` business limits (`banking.constraints.*`) | nothing |
| `bff` | Portal orchestration: controller + service + record DTOs + CORS/config; composes services in process | `service`, `repository` (read-only facades), `domain`, `request`, `messages` |
| `gateway` | Servlet filters + config (not controllers) | `messages` |

`contoller` also hosts `StaffController` (employee-facing, bearer JWT via `StaffAuthenticationFilter`) and `LoginController`; see `employees-and-logins.md`.

## Dependency rules
- Dependencies point downward only. Controllers never touch repositories; services never touch `repository.jpa` or entities.
- Entities never leak past the repository facade. Public facade signatures use domain objects.
- Controllers never return entities. Return domain objects or view DTOs.
- Constructor injection via Lombok `@RequiredArgsConstructor`. No field `@Autowired`.
- Business limits (min balance, max cash deposit, min age, statement range) live in `AccountConstraints` / `application.yml`, not as literals in services.

## Invariants (do not break)
- Account numbers carry the type: `CH-`/`SV-` + at least 10 zero-padded digits. Withdraw/deposit derive type from the prefix.
- One `AccountEntity` row per real account; the `Account` domain object pairs checking/saving fields.
- `AccountStatus.CLOSED` blocks withdraw/deposit before any balance check. No reopen path.
- Every new banking endpoint path must be added to `BankingGatewayConfig.BANKING_URL_PATTERNS` (staff: `/v1/api/staff/*`, customer login: `/v1/api/customers/*`).
- Employee/login rules (privileges derive from the role; only an ACTIVE employee with an ACTIVE login acts; customer-initiated deposit/withdraw need an ACTIVE login when the owner has one) are in `employees-and-logins.md`. Credentials live in their own tables and never leave the service/facade layers.
- Anything that changes a field in `AccountStatusView` must evict `AccountStatusStatementService.ACCOUNT_SEARCH_CACHE`.
- Schema or seed change → update `db/` scripts and `banking-openapi.yaml` in the same change.

## Staff and logins (see `employees-and-logins.md`)
```
staff request → StaffAuthenticationFilter (JWT) → StaffController → StaffAccountService / StaffLoginService / EmployeeService.requirePrivilege
              → (privilege + ACTIVE employee + ACTIVE login) → ClientAccountService / AccountSuspensionService (unchanged rules)
customer request (no handler) → ClientAccountService → CustomerCredentialService.requireActiveLoginIfPresent(owner)
```

## Account lifecycle (see `account-lifecycle.md`)
- `AccountStatus` is `ACTIVE`/`SUSPENDED`/`CLOSED`. A **suspended account can't transact** until it is ACTIVE again; suspension data (`suspended` flag, start/end, notes) is cleared on reactivate/close.
- `AccountSuspensionService` owns suspend/update/reactivate (+ the scheduled expiry job); `ClientAccountService` keeps registration, withdraw/deposit and close.
- Changing an enum column needs a hand-written `db/ddl` migration; `ddl-auto: update` won't alter existing enum columns.
