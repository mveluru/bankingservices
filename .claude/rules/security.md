# Security requirements
General: validate input at the edge, parameterized queries only, no secrets in code or logs, no stack traces in responses.

Banking specifics (PII masking, `X-Customer-Id` is not auth, money handling) are in `rules/banking/security.md`.
