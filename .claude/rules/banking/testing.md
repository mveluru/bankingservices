---
paths:
  - "src/test/java/org/brite/banking/**"
---
# Banking: testing conventions

JUnit 5 + Mockito + AssertJ/Jupiter assertions. Package of the test mirrors the class under test (known exception: `AccountStatusStatementServiceTest` lives in `.../banking/statement/`; leave it).

## Choose the lightest test that proves it
| Layer | Style | Needs MySQL? |
|---|---|---|
| Service | Plain unit test, `mock(XRepository.class)`, construct the service directly. No Spring. | No |
| Controller | Standalone MockMvc: `MockMvcBuilders.standaloneSetup(new XController(mockService)).setControllerAdvice(new BankingExceptionHandler()).setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())`, Jackson configured like Boot (JavaTime, ISO dates). See `BankLocationControllerTest`. | No |
| Repository facade / Specification | `@DataJpaTest` on embedded H2 (see `AccountRepositoryTest`, `LocationBasedOperationRepositoryTest`): real SQL, `ddl-auto=create-drop`, MySQL dialect override cleared. Construct the facade around the `@Autowired` JPA repo in `@BeforeEach`. | No (H2) |
| Seeder | Run against H2 (`BankLocationDataSeederTest`) | No |
| Gateway filter | `MockHttpServletRequest/Response` + stand-in chain; logging via Logback `ListAppender`; clear MDC in `@AfterEach` | No |
| Caching/AOP annotations | Reflection or direct call on the aspect | No |
| Full wiring | `@SpringBootTest` + `MockMvc`: only for wiring checks, sparingly | **Yes** |

Do not mock `*JpaRepository` to test a `Specification`; a mock can't prove the SQL.

## What to cover
- Service: each rule's pass and fail (boundary values for age, min balance, deposit cap, month ranges), exact exception type **and message** (via `BankingMessages`), status transitions, verify collaborator interactions and `never()` on side effects when rejected.
- Controller: param binding (enums, dates, paging defaults such as size 20), JSON shape, status codes incl. 400/404 via the real advice, plain-text error bodies.
- Repository: each filter alone and combined, empty result, sort allow-list accept/reject, pagination, case-insensitivity, capability matching (`type`).
- Gateway: missing header → 400, exhaustion → 429, headers present, btid set then cleared.
- Bug fix → add the regression test first.

## Style
- Arrange/act/assert separated by blank lines; one behaviour per test; no logic in tests.
- Test data via domain `@Builder`s; use `BigDecimal` with explicit scale; dates relative to `LocalDate.now()` when the code under test uses "today".
- No `Thread.sleep`; no reliance on seeded MySQL data or test order.
- Run: `mvn test -Dtest=ClassName#method`; the full suite needs MySQL up, the banking unit/H2 tests do not.

## Suspension coverage (keep when touching the lifecycle)
- Repository (H2): suspend sets status/flag/start/end/notes; already-suspended and closed rejections; **withdraw and deposit on a suspended account throw and leave the balance unchanged**; reactivate clears fields and re-enables transactions; partial update; end-before-stored-start; close-from-suspended; expiry reactivates only finished suspensions (not running or indefinite ones); `status=SUSPENDED` search.
- Service (Mockito): default start, no future start, end after start and in the future, empty update rejected with no repository call, reactivate and expiry delegation.
- Controller (standalone MockMvc): body binding incl. ISO `LocalDateTime`, blank/over-500 notes → 400 before the service, plain-text 400/404 via the real `BankingExceptionHandler`.
- Seeders (H2, minimal `@Import` context): exact counts (92 accounts; 22 CLOSED; 20 SUSPENDED), suspended rows not pre-expired, rerun never duplicates and restores a deleted row.
- Caching: every `AccountSuspensionService` mutation asserts `@CacheEvict(ACCOUNT_SEARCH_CACHE, allEntries = true)` (`AccountSearchCachingTest`).
