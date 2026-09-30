---
paths:
  - "src/main/java/org/bee/banking/**"
  - "src/test/java/org/bee/banking/**"
---
# Banking module: architecture

Vertical slice under `org.bee.banking`. Request flow:

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
- Every new banking endpoint path must be added to `BankingGatewayConfig.BANKING_URL_PATTERNS`.
- Anything that changes a field in `AccountStatusView` must evict `AccountStatusStatementService.ACCOUNT_SEARCH_CACHE`.
- Schema or seed change → update `db/` scripts and `banking-openapi.yaml` in the same change.
