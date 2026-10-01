---
paths:
  - "src/main/java/org/brite/banking/contoller/**"
  - "src/main/java/org/brite/banking/request/**"
  - "src/main/java/org/brite/banking/domain/**"
  - "src/main/resources/static/openapi/**"
---
# Banking: REST conventions and OpenAPI sync

- Base: context path `/brite`, port 8081. Versioned paths `/v1/...`. Resource nouns plural: `/accounts`, `/locations`. Actions on a resource are `POST /{id}/<verb>` (`/accounts/{n}/close`, `/suspend`, `/reactivate`); a partial update of a sub-resource is `PATCH` (`/accounts/{n}/suspension`); bulk is `POST /accounts/close`.
- Search = `GET` collection with optional filters + `Pageable` (`page`, `size`, `sort=field,dir`). Response is Spring's `Page` JSON. Default size 20; document valid sort keys.
- Filter semantics: text case-insensitive equality; all filters AND'd; omitted filter = no restriction (except the documented `months` lookback default on account search).
- JSON: ISO `yyyy-MM-dd` dates, `MM/dd/yyyy` only for `dateOfBirth`; enums as upper-case strings; money as numbers with decimal scale.
- Errors: business failures = plain-text body with status per `BankingExceptionHandler`; `@Valid` failures = Spring default JSON; gateway rejections = plain text (`400` missing header, `429` limit).
- Required header on banking endpoints: `X-Customer-Id`. Staff endpoints (`/v1/api/staff/*` except login) also require `Authorization: Bearer <employee JWT>` (the acting employee is the token's subject; `401` without a valid one, `403` for a customer token). Login: `POST /v1/api/staff/login`, `/v1/api/customers/login` (verify, return a JWT + the profile; `Cache-Control: no-store`; the employee token is required by the other staff endpoints, which declare the `BearerAuth` scheme; nothing requires the customer token yet). Login status: `PUT .../login-status`. Responses carry `X-BTID`, `X-RateLimit-*`.

## OpenAPI is hand-written: update it with the code
`src/main/resources/static/openapi/banking-openapi.yaml` (3.0.3) is not generated. In the same change as any controller/DTO/validation/exception change:
1. Paths, params (with enums and defaults), request/response schemas, `required` lists, status codes.
2. `text/plain` vs JSON error split.
3. Quote YAML flow-sequence items containing `,` and scalars containing `: `.
4. Validate: `pip install openapi-spec-validator && openapi-spec-validator src/main/resources/static/openapi/banking-openapi.yaml`.
5. Update the README endpoint table and sample `curl`.
6. Staff/login paths use tags `Staff` and `Login`, the `EmployeeNumber` apiKey security scheme and the `EmployeeForbidden`/`LoginNotActive` responses; keep the `EmployeeRole`/`EmployeePrivilege`/`LoginStatus` enums equal to the Java enums.
