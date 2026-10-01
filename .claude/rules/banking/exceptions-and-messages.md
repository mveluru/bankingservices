---
paths:
  - "src/main/java/org/brite/banking/exception/**"
  - "src/main/java/org/brite/banking/messages/**"
---
# Banking: exceptions and messages

## Exceptions
- One typed unchecked exception per business failure (`AccountNotFoundException`, `AccountClosedException`, `InsufficientFundsException`, `MinBalanceException`, `MaxDepositAmountException`, `AgeException`, `StatementRangeExceededException`, `LocationNotFoundException`, `EmployeeNotFoundException`, `CustomerNotFoundException`, `EmployeeNotAuthorizedException`, `LoginNotActiveException`, `InvalidCredentialsException`, `EmployeeLockedException`, `InvalidTokenException`, `BankServiceUnavailableException`). Extend `RuntimeException`, take a message only.
- Mapping lives solely in `BankingExceptionHandler` (`@RestControllerAdvice(basePackages = "org.brite.banking")`): each handler logs `warn` with a `LOG_HANDLER_*` constant and returns `ResponseEntity<String>` (plain text body).

| Exception | Status |
|---|---|
| `AccountNotFoundException`, `LocationNotFoundException`, `EmployeeNotFoundException`, `CustomerNotFoundException` | 404 |
| `InvalidCredentialsException` | 401 (one message for unknown user and wrong password) |
| `InvalidTokenException` | 401 (bad, expired, forged or wrong-kind token; thrown by `JwtService.parse`, no endpoint calls it yet) |
| `EmployeeNotAuthorizedException`, `LoginNotActiveException` | 403 |
| `EmployeeLockedException` (also used for customer logins) | 423 |
| `AccountClosedException`, `AccountSuspendedException`, `InsufficientFundsException`, `MinBalanceException`, `MaxDepositAmountException`, `AgeException`, `StatementRangeExceededException`, `IllegalArgumentException` | 400 |
| `BankServiceUnavailableException` | no handler; thrown by `BankClient` in the payment resilience demo |

- New exception → add class, handler, `LOG_HANDLER_*` constant, and the response in `banking-openapi.yaml` (as `text/plain`).
- `@Valid` body failures are not handled here; Spring's default JSON error applies (`SpringErrorResponse` in the spec).
- Do not catch-and-rethrow in services just to log; the handler logs.
- Never put PII (DOB, full name, balances of other accounts) or any password/hash in exception text; credential failures use the single `INVALID_CREDENTIALS` message.

## `BankingMessages`
- Single home for exception text, validation text, and log templates. `final` class with private constructor, `public static final String`.
- Group with the existing section comments; keep `String.format` (`%s`, `%d`) vs SLF4J (`{}`) styles separate.
- Reuse before adding. Names describe the condition (`ACCOUNT_ALREADY_CLOSED`), not the call site.
- Bean Validation annotations reference `VALIDATION_*` constants, which must be compile-time constants.
