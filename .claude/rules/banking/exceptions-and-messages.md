---
paths:
  - "src/main/java/org/bee/banking/exception/**"
  - "src/main/java/org/bee/banking/messages/**"
---
# Banking: exceptions and messages

## Exceptions
- One typed unchecked exception per business failure (`AccountNotFoundException`, `AccountClosedException`, `InsufficientFundsException`, `MinBalanceException`, `MaxDepositAmountException`, `AgeException`, `StatementRangeExceededException`, `LocationNotFoundException`, `BankServiceUnavailableException`). Extend `RuntimeException`, take a message only.
- Mapping lives solely in `BankingExceptionHandler` (`@RestControllerAdvice(basePackages = "org.bee.banking")`): each handler logs `warn` with a `LOG_HANDLER_*` constant and returns `ResponseEntity<String>` (plain text body).

| Exception | Status |
|---|---|
| `AccountNotFoundException`, `LocationNotFoundException` | 404 |
| `AccountClosedException`, `InsufficientFundsException`, `MinBalanceException`, `MaxDepositAmountException`, `AgeException`, `StatementRangeExceededException`, `IllegalArgumentException` | 400 |
| `BankServiceUnavailableException` | no handler; thrown by `BankClient` in the payment resilience demo |

- New exception → add class, handler, `LOG_HANDLER_*` constant, and the response in `banking-openapi.yaml` (as `text/plain`).
- `@Valid` body failures are not handled here; Spring's default JSON error applies (`SpringErrorResponse` in the spec).
- Do not catch-and-rethrow in services just to log; the handler logs.
- Never put PII (DOB, full name, balances of other accounts) in exception text.

## `BankingMessages`
- Single home for exception text, validation text, and log templates. `final` class with private constructor, `public static final String`.
- Group with the existing section comments; keep `String.format` (`%s`, `%d`) vs SLF4J (`{}`) styles separate.
- Reuse before adding. Names describe the condition (`ACCOUNT_ALREADY_CLOSED`), not the call site.
- Bean Validation annotations reference `VALIDATION_*` constants, which must be compile-time constants.
