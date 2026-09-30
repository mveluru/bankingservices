# Testing conventions
General: JUnit 5 + Mockito. Prefer plain unit tests; use `@DataJpaTest` on H2 for real query behavior; reserve `@SpringBootTest` (needs live MySQL) for wiring checks. Retail's static catalog leaks state across tests, so avoid exact-size assertions there.

Banking test matrix per layer is in `rules/banking/testing.md`. Run one test: `mvn test -Dtest=Class#method`.
