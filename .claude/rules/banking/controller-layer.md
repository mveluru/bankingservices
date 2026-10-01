---
paths:
  - "src/main/java/org/brite/banking/contoller/**"
---
# Banking layer: controllers (`org.brite.banking.contoller`)

- `@RestController @RequestMapping("/v1/...") @RequiredArgsConstructor`. Only routing, binding, `@Valid`, and wrapping in `ResponseEntity`.
- No business rules, no repository access, no try/catch. Throw from the service; `BankingExceptionHandler` maps it.
- Path roots in use: `/v1/api/accounts` (incl. `/{n}/suspend`, `PATCH /{n}/suspension`, `/{n}/reactivate`), `/v1/api/locations`, `/v1/client`, `/v1/payment`, plus `/notify`, `/notify-sms`, `/report`. New endpoints follow `/v1/api/<resource>`.
- Bodies: `@Valid @RequestBody <X>Request`. Query filters: `@RequestParam(required = false)` with typed enums/`LocalDate` (`@DateTimeFormat(iso = ISO.DATE)`).
- Lists: return `Page<T>` with `@PageableDefault(size = 20, sort = "<key>")`. Never return unbounded lists.
- One canonical way to fetch a record. Do not add a path-variable status endpoint for accounts; `GET /v1/api/accounts?accountNumber=` is the way. Locations do have `GET /{id}` (stable numeric id).
- Status codes: `200` for reads and actions with per-item results (bulk close is always `200`), `201` for account registration (`POST /v1/api/accounts`); don't change existing endpoints' codes, `400` bad input/business rule, `404` missing, `429` rate limited.
- A Javadoc block above each handler with an example `GET/POST` line, matching the README.
- Add the path to `BankingGatewayConfig.BANKING_URL_PATTERNS`, then update `banking-openapi.yaml` and README endpoint table.
- `ExecutionTimeLoggingAspect` already times every `@RestController`; add no timing code.
