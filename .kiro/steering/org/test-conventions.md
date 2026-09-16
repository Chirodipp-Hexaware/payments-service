---
inclusion: always
---

# Test Conventions

How the org writes and runs tests. Always loaded into Kiro context. The
`test-on-save` hook runs the relevant suite on file save.

## Expectations

- New features and bug fixes ship with tests. A bug fix includes a test that
  fails before the fix and passes after.
- Do not add tests to code the user did not ask to change unless it is needed to
  make the change safe.

## Structure

- Follow **Arrange–Act–Assert**. One logical assertion group per test.
- Test names describe behavior: `returns 404 when order is not found`.
- Keep tests deterministic: no reliance on wall-clock time, network, or ordering.
  Mock external services and clock.

## Coverage

- Target **80% branch coverage** minimum on changed code. Coverage is a floor,
  not a goal — cover the meaningful branches (error paths, boundaries), not just
  the happy path.
- Security-sensitive logic (auth, input validation, PII masking) requires
  explicit tests for the failure branches.

## Frameworks

- **TypeScript/Node.js**: Jest or Vitest with `--run` (no watch mode in CI).
- **Java**: JUnit 5 + Mockito; enforce coverage with JaCoCo in `verify`.

## Layers

- **Unit**: pure logic, fully mocked collaborators. Fast, run on every save.
- **Integration**: real adapters against local/test doubles (e.g. DynamoDB Local).
- **Contract/E2E**: reserved for critical paths; run in CI, not on save.

## Running

- Local single run: use the project's test task (e.g. `npm test -- --run`,
  `mvn verify`). Never leave a watch process running as part of automation.
