---
paths:
  - "src/main/java/org/brite/banking/**"
  - "src/test/java/org/brite/banking/**"
---
# Banking module: code style

Match the surrounding code. Java 25, Spring Boot 3.5, 4-space indent, no tabs, no wildcard imports in new files.

## Naming
- Packages: lowercase. **Keep the existing typos** `contoller` and `validtors`; do not rename or "fix" them.
- Never use `@Data`/`toString` on anything holding a password or hash (`@ToString(exclude = ...)`).
- Classes: `*Controller`, `*Service`, `*Repository` (facade), `*JpaRepository` (Spring Data), `*Entity`, `*Embeddable`, `*Request`, `*Exception`, `*Filter`, `*Mapper`, `*Seeder`.
- Constants: `UPPER_SNAKE` in `BankingMessages`; prefixes `LOG_*`, `VALIDATION_*`, none for exception text.
- Test classes: `<ClassUnderTest>Test`; methods are camelCase sentences, e.g. `getLocationThrowsLocationNotFoundWhenMissing`.

## Lombok
- Class-level: `@RequiredArgsConstructor` for Spring beans, `@Slf4j` for logging (never `LoggerFactory` by hand).
- Domain/request: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` (`@Data` acceptable on request DTOs and `@ConfigurationProperties`).
- Entities: no `@Data`, no `@EqualsAndHashCode` over relationships.
- Domain objects that need identity semantics declare `@EqualsAndHashCode(of = {...})` explicitly.

## Strings and logging
- No string literals for user-visible errors, validation text or log templates in code: add a constant to `BankingMessages` (grep first for an existing one).
- Exception text uses `String.format` (`%s`, `%d`); log templates use SLF4J `{}` placeholders.
- Levels: `debug` business-flow tracing, `info` state changes and request summaries, `warn` handled business rejections (in `BankingExceptionHandler`), `error` unexpected failures.
- Never log balances alongside PII, dates of birth, full request bodies (the request filter masks and truncates), or headers.
- Do not add `btid` manually; MDC propagates it.

## Java idioms
- `BigDecimal` compared with `compareTo`, never `equals`.
- Prefer `Optional` returns from repository lookups; throw the typed exception in the service.
- Use `switch` expressions over enums (no `default` when exhaustive, so new enum values fail compilation).
- `LocalDate.now()` for "today"; for seed data use `LocalDate.now().minusMonths(N)`, never fixed dates.
- Javadoc on public methods explains the *why* and the thrown exception with its HTTP mapping (`@throws X (mapped to 404)`). Skip comments that restate the code.
- No unused imports; remove dead duplicates when touching a file.
