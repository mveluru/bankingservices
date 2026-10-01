---
paths:
  - "src/main/java/org/brite/banking/bff/**"
  - "src/test/java/org/brite/banking/bff/**"
---
# Banking layer: BFF / orchestration for the banking UI portal (`org.brite.banking.bff`)

Backend-for-frontend for the React "banking UI portal". Composes existing banking services **in process** (no HTTP hop) into one payload per screen. Under `/bff/v1/portal`.

```
bff.controller  PortalController (accounts) / CustomerPortalAuthController (customer login, login status, password)  (routing, @Valid, ResponseEntity)
  → bff.service.PortalOrchestrationService  (composition + mapping only)
      → ClientAccountService / AccountStatusStatementService / LocationBasedOperationService
      → TransactionRepository (facade, read-only activity)
  → bff.service.CustomerLoginPortalService / CustomerLoginStatusPortalService / CustomerPasswordPortalService  (login + home in one call; delegate the rest)
      → LoginService / CustomerCredentialService / PasswordResetService / StaffLoginService
  → bff.dto  (records shaped for the UI)
bff.config  PortalProperties (banking.portal.*), PortalCorsConfig
```

## Keeping the BFF in step with banking
- **Two portals, two prefixes.** `/bff/v1/portal/*` is the customer portal (customer token, `CustomerAuthenticationFilter`), `/bff/v1/staff/*` is the staff portal (employee token, `StaffAuthenticationFilter`; controllers `StaffPortalController` + `StaffPortalAuthController`, services `StaffPortalService` + `StaffPortalAuthService`). Never serve staff and customer screens from the same prefix: each filter rejects the other's token with 403. The staff BFF mirrors the banking staff API (account actions, employee cards, people administration, login/password calls) and returns the refreshed overview after an account action; it must call the banking staff service **first** (it enforces the privilege) and only then build anything. Employees are shown as `PortalEmployee` cards (no email, phone, internal ids, supervisor link).
- **One controller for customer credentials, across both portals.** `CustomerPortalAuthController` serves customer *login*, *login status* and *password*: customer sign-in, change password, security questions, catalog and reset under `/bff/v1/portal`, and staff create-login, set-status and set-password under `/bff/v1/staff`. It injects `CustomerLoginPortalService`, `CustomerLoginStatusPortalService` and `CustomerPasswordPortalService`, which stay separate delegating services: keep the controller to routing and the services to delegation. Its handlers mix customer-token and employee-token paths on purpose (a handler reads only its own attribute: `CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE` or `StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE`); `StaffPortalController` keeps accounts and employee cards only; the staff portal's employee credential calls (own login, password, security questions, reset **and** an administrator setting another employee's login status or password) all live in `StaffPortalAuthController`, which injects `StaffPortalAuthService` and, for the two administrator calls, the unchanged `StaffPortalService`. The question catalog is `SecurityQuestionView.catalog()`, shared with the staff portal.
- **Every customer-facing banking capability needs a portal counterpart**, in screen shape: today accounts (home, overview, open, withdraw, deposit, close, statement) and access (login + home in one call, change password, security questions + catalog, password reset). When a customer-facing banking endpoint is added or changes, update `PortalController`/`PortalAuthController`, the DTOs, the OpenAPI `Portal (BFF)` operations, the README portal rows, `PortalCorsConfig` (a new verb or header) and `CustomerAuthenticationFilter.OPEN` (a new no-token portal path), with tests. Staff-only banking features (suspend, reactivate, staff login, admin password) are mirrored only in the staff portal, never in the customer portal.
- **Never expose staff identity to customers.** Anything shown to a customer shows the branch/ATM (`bankLocationName/Type/City/State`) but not the employee: portal DTOs have no employee field, and `BankStatementService.generateStatement` clears `employeeNumber/Name/Role` on every statement line (so the banking and portal statements both hide staff identity).
- A DTO that carries a token (`PortalLoginResponse`) must keep it out of `toString`; login responses are `Cache-Control: no-store`.

## Rules
- **No business rules in the BFF.** Validation, limits, status checks and notifications stay in the banking services it calls. The BFF only composes, shapes and applies UI limits (`banking.portal.*`).
- **Reads must be side-effect free.** Don't call `BankStatementService.generateStatement` from a read path: it sends email/SMS. Use the repository facade for activity.
- DTOs are Java `record`s in `bff.dto`; never return entities or reuse full domain graphs when a slimmer UI shape exists (`PortalLocation`, `PortalAccountSummary`). No balances in anything derived from the cached `AccountStatusView`.
- The account overview and home rows show `suspended`/`suspendedUntil` so the portal can disable transact buttons; don't expose `suspensionNotes` (internal) in any BFF payload. Phone numbers go out masked only (`maskedPhoneNumber`, last four digits via `PortalOrchestrationService.maskPhone`).
- `statement` is the one portal endpoint with a notification side effect (`BankStatementService.generateStatement` emails/SMSes), so it is a `POST` (`accounts/{n}/statement?beginDate=&endDate=`) returning the `BankStatement`; keep it out of `GET`/read paths.
- One endpoint per portal screen; add fields to the screen's DTO rather than adding chatty endpoints.
- Errors: throw the existing typed exceptions with `BankingMessages` text; `BankingExceptionHandler` already covers `org.brite.banking.bff` (plain text 400/404). Add `PORTAL_*`/`LOG_PORTAL_*` constants, no inline strings.
- Tunables (limits, allowed origins) go in `PortalProperties` + `application.yml` under `banking.portal`, not literals.
- Every new path needs `BankingGatewayConfig.BANKING_URL_PATTERNS` (`/bff/v1/portal/*` covers sub-paths), `banking-openapi.yaml` (tag `Portal (BFF)`), and the README table.
- Mutating passthroughs (implemented: `withdraw`, `deposit`, `close`; suspend, update-suspension and reactivate are staff-only and deliberately not offered): call the existing service method (it owns the rules, cache eviction and notifications), then return `overview(accountNumber, null)` so the portal redraws from one response. If the service throws, propagate it (no overview is built). Reuse the banking request types (`WithdrawalRequest`, `DepositForm`, `SuspendAccountRequest`, `UpdateSuspensionRequest`) instead of cloning them.

## CORS
- `PortalCorsConfig` maps `/bff/**` for `banking.portal.allowed-origins`, methods GET/POST/PATCH/OPTIONS (add a method here whenever a portal endpoint uses a new verb), allowed headers `Content-Type`+`X-Customer-Id`+`Authorization`, exposes `X-BTID` and `X-RateLimit-*`.
- Preflights carry no custom headers, so `BankingRateLimitFilter` passes `OPTIONS` + `Access-Control-Request-Method` through. Limitation: a `400`/`429` written by the rate-limit filter has no CORS headers, so the browser can't read it; the portal should treat a CORS/network failure on `/bff` as possibly rate limiting.
- Don't use `*` origins; list the portal hosts.

## Auth
- Every portal endpoint except `POST accounts/open` requires `Authorization: Bearer <customer JWT>` (`CustomerAuthenticationFilter`, which also re-checks the customer's login is ACTIVE on every request). The BFF is therefore **not** a place to trust a client-supplied identity: every handler takes the customer from `CustomerAuthenticationFilter.CUSTOMER_ATTRIBUTE` and calls `CustomerAccessService.requireOwnAccount` before delegating (someone else's account → 403); `home` is scoped to the customer (`PortalOrchestrationService.home(state, customerId)`).
- `PortalCorsConfig` allows the `Authorization` header (preflights carry no custom headers, so the filters let `OPTIONS` + `Access-Control-Request-Method` through). A `401`/`403` written by a filter has no CORS headers, so the browser can't read it: the portal should treat a CORS/network failure on `/bff` as possibly an expired token or rate limiting and log in again.
- `X-Customer-Id` is still required by the rate limiter and is only a rate-limit key, never an identity.

## Tests
- Staff portal: `StaffPortalServiceTest` (delegation, banking action before overview, rejection builds nothing, VIEW_ACCOUNT, cards without email/phone), `StaffPortalAuthServiceTest`, `StaffPortalControllerTest` (status mapping, acting employee from the request attribute, and a run with the real `StaffAuthenticationFilter`), `StaffPortalAuthControllerTest`.
- `CustomerLoginPortalServiceTest` / `CustomerPortalAuthControllerTest` (login composes token + customer + home, a failed login never builds home, no-store, status mapping; staff create-login 201/403/400; staff set login status 200/403/404/400; then the password calls: delegation with the customer id from the token, fail-closed 401, open routes, validation; staff set-password 204/403/400), `CustomerAuthenticationFilterTest` (the open portal paths and that `PUT` password/questions need a token).
- Overview: activity shows the branch/ATM and no employee field exists (`PortalOrchestrationServiceTest`); the statement hiding is tested where it happens, in `BankStatementServiceTest`.
- Service: plain Mockito (`PortalOrchestrationServiceTest`), including the not-found and bad-`days` paths, that rejected input never calls collaborators, that each mutation delegates then returns the refreshed overview, and that a suspended-account rejection propagates without building an overview.
- Controller: standalone MockMvc with `BankingExceptionHandler` (`PortalControllerTest`).
