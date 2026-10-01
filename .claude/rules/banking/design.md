---
paths:
  - "src/main/java/org/brite/banking/domain/**"
  - "src/main/java/org/brite/banking/entity/**"
  - "src/main/java/org/brite/banking/request/**"
  - "src/main/java/org/brite/banking/component/**"
  - "src/main/java/org/brite/banking/rules/**"
---
# Banking module: design (domain, entity, request, mapping)

## Three shapes, three jobs
- **Request** (`request/`): inbound JSON + validation. Never persisted, never returned.
- **Domain** (`domain/`): what services/controllers pass around and return. Lombok `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`; `Serializable` when cached.
- **Entity** (`entity/`): JPA only. Suffix `Entity` / `Embeddable`. Never referenced outside `repository`.

Adding a persisted field to a domain object means: add to the entity, both mapper directions in the repository facade, `db/ddl/01_create_tables.sql`, seed SQL if seeded, and the OpenAPI schema.

## Entities
- `@Entity @Table(name = "snake_case_plural")`, `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`. No `@Data` (avoids `equals`/`hashCode` over lazy state).
- `@Enumerated(EnumType.STRING)` always, never ordinal.
- `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` for MySQL.
- Mutable aggregates that receive concurrent writes get `@Version` (see `AccountEntity`).
- Repeated address shape → `@Embeddable`, not a new table.
- Collections of enums → `@ElementCollection` + `@CollectionTable`; use `@Builder.Default` for collection/default fields.
- Money is `BigDecimal`. Never `double`/`float`.
- Dates are `LocalDate`/`LocalTime`. Store a zone id string (e.g. `America/Chicago`), never a fixed offset or "CST".

## Request DTOs
- `@Data @Builder @NoArgsConstructor @AllArgsConstructor`.
- Every constraint has `message = BankingMessages.VALIDATION_*`. No inline message strings.
- `dateOfBirth` is `MM/dd/yyyy`; every other date is ISO `yyyy-MM-dd`. `phoneNumber` is strictly `###-###-####` (required on registration, nullable in the domain/DB for legacy customers).
- Custom constraints go in `domain.validtors` (package name is a known typo; keep it).

## Mapping
- Request → domain: MapStruct (`@Mapper(componentModel = "spring")` in `component/`). Explicitly `ignore` unmapped targets; the build should stay warning-free.
- Entity ↔ domain: hand-written `toDomain`/`toEntity` inside the repository facade.
- Enum-from-string conversions go through a `@Named` method that normalises case.

## Enums and views
- Domain enums (`AccountType`, `AccountStatus`, `LocationType`, `BankOperationServices`, `TransactionType`, `EmployeeRole`, `EmployeePrivilege`, `EmployeeStatus`, `LoginStatus`) are the single source of truth for allowed values; the OpenAPI enum lists must match.
- Read-only projections (e.g. `AccountStatusView`) are separate classes. Do not expose balances in anything cached.

## Employees and logins
- Employee profile, credentials and customer credentials are three separate tables; credentials are never part of a profile or response object (`EmployeeCredential`/`CustomerCredential` exclude the hash from `toString`; `AuthenticatedCustomer` carries only id + name). Cross-table references are plain id columns, not foreign keys.
- Privileges are derived from `EmployeeRole`, never stored. `TransactionHandler` (employee + branch/ATM snapshot) is copied onto `AccountTransaction`/`AccountTransactionEntity`; snapshots, not references.
- Status columns use `@ColumnDefault("'ACTIVE'")` so rows that predate the column stay active. Details: `employees-and-logins.md`.

## Configuration
- Business limits: `@ConfigurationProperties(prefix = "banking.constraints")` (`AccountConstraints`), bound with `@ConfigurationPropertiesScan`.
- Gateway toggles: `banking.rate-limit.*`, `banking.request-logging.*`.
- Login tokens: `banking.jwt.*` (`JwtProperties`: `secret` from `BANKING_JWT_SECRET`, `issuer`, `expiration-minutes`).
- Login lockout: `banking.employee-login.*` and `banking.customer-login.*` (`max-failed-attempts`, `lockout-minutes`; `EmployeeLoginProperties`/`CustomerLoginProperties` in `rules`).
