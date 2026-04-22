# Ralph Test-Only Spring Bugfix Plan

## Goal
Improve backend test quality with the `spring-bugfix` workflow while keeping the change set strictly test-only.

This is not a generic cleanup pass. Ralph must choose one concrete backend behavior that is weakly covered, then add or improve the narrowest regression test that proves the behavior or exposes the bug.

## Hard Scope
Allowed paths:
- `backend/cvect/src/test/java/**`
- `backend/cvect/src/test/resources/**`
- `plan.md` only if Ralph needs to record a narrow plan update before editing tests

Forbidden paths:
- `backend/cvect/src/main/**`
- `frontend/**`
- `Qwen/**`
- `docker-compose.yml`
- `AGENTS.md`
- `PROMPT.md`
- `ralph.yml`
- database migrations, production config, scripts, or deployment files

If a test exposes a real production bug, do not edit production code in this loop. Stop after the smallest useful failing/regression evidence and report the exact failing path, root cause hypothesis, and recommended production fix.

## Spring Bugfix Workflow
Before editing tests, Ralph must write down:
- Bug statement: one sentence describing the suspected behavior gap.
- Smallest failing path: controller/service/repository method and test class involved.
- Regression target: the exact assertion that should fail before a production fix or prevent a regression.

Then execute one focused cycle:
1. Inspect only the relevant production code and existing tests needed to understand the behavior.
2. Choose exactly one weakly covered behavior.
3. Add or improve one focused test class or one focused group of tests in an existing class.
4. Keep any refactor local to the touched test class unless a same-package test helper already exists.
5. Run the smallest relevant Maven test command.
6. If the failure is caused by a production bug, do not change production code; document the failure.
7. If the failure is caused by an incorrect/flaky test, fix only the test.
8. Rerun the same narrow verification.

## Test Refactor Rules
- Prefer modifying existing tests over creating new broad suites.
- Do not move packages or rename production-facing types.
- Do not introduce new abstractions for single-use test setup.
- Extract a helper only when it removes repeated setup inside the same test class.
- Assertions must check behavior, not implementation details, unless the behavior is SQL generation or cache key construction.
- Keep data deterministic: fixed UUIDs, stable ordering, explicit timestamps where needed.
- Avoid sleeps, real network calls, and dependence on local Docker availability unless the existing test class already uses Testcontainers assumptions.
- Do not weaken existing assertions just to make tests pass.

## Good Candidate Areas
Pick only one:
- validation and error paths in service-layer tests
- security or permission behavior in controller tests
- tenant or JD scoping in repository/service tests
- vector search scoring, filtering, cache key, or index SQL behavior
- upload queue idempotency and failure recovery tests
- parser/chunker boundary conditions with deterministic sample input

Avoid areas already fully covered in the latest loop unless Ralph finds a specific missing assertion.

## Current Test Review Baseline
Command run:

```bash
cd backend/cvect && ./mvnw -q test
```

Observed result:
- `47` test classes
- `181` tests
- `0` failures
- `0` errors
- `7` skipped

The suite is green, but review found test bugs that can hide regressions. Treat the items below as `bugfix` work, not generic cleanup.

## Reviewed Bugfix Backlog

### [bugfix:test-vector-integration-false-positive]
Bug statement: `VectorStoreServiceIntegrationTest` claims to exercise PostgreSQL + pgvector behavior, but in the no-Docker H2 fallback it still runs `7` tests with `0` skipped because it only checks `vectorStore != null`.

Smallest failing path:
- `backend/cvect/src/test/java/com/walden/cvect/infra/vector/VectorStoreServiceIntegrationTest.java`
- `should_save_experience_vector_from_pdf`
- `should_save_skill_vector_from_pdf`
- `should_store_vectors_from_pdf_pipeline`
- `should_create_vector_index`
- `should_delete_vectors_by_candidate`
- `should_support_candidate_isolation`
- production path under test: `VectorStoreService.save`, `VectorStoreService.createVectorIndex`, `VectorStoreService.deleteByCandidate`

Why this is a test bug:
- `PostgresIntegrationTestBase` sets `app.vector.enabled=false` when Docker is unavailable.
- `VectorStoreService` still exists as a Spring bean, so `Assumptions.assumeTrue(vectorStore != null)` does not skip.
- `save` can return `false` or index creation can no-op while the tests pass because most assertions only check `doesNotThrow`.

Regression target:
- Add a test-local readiness helper that requires `vectorStore != null && vectorStore.isOperational()`.
- Replace vector integration assumptions with that helper.
- Where a save path is expected to persist a vector, assert `vectorStore.save(...)` returns `true`.
- Keep this change test-only.

Verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=VectorStoreServiceIntegrationTest test
```

Expected proof:
- Without PostgreSQL + pgvector, vector integration tests should be skipped instead of fake-passing.
- With PostgreSQL + pgvector, vector save/index tests must assert successful behavior, not only absence of exceptions.

### [bugfix:test-error-handling-swallowed-exceptions]
Bug statement: `VectorStoreServiceErrorHandlingTest` catches and ignores exceptions in several tests, so the test can pass without proving the expected behavior.

Smallest failing path:
- `backend/cvect/src/test/java/com/walden/cvect/infra/vector/VectorStoreServiceErrorHandlingTest.java`
- `should_not_throw_exception_when_deleting_nonexistent_candidate`
- `should_throw_exception_on_database_error_during_index_creation`
- `should_handle_empty_content_string`
- `should_handle_very_long_content_string`

Why this is a test bug:
- Empty `catch (Exception e)` blocks make both success and failure paths pass.
- Some display names say "should throw" while the body accepts both throw and no-throw.
- This prevents Ralph from detecting real error-handling regressions.

Regression target:
- Replace swallowed exceptions with explicit assertions:
  - `assertThatCode(...).doesNotThrowAnyException()` when no exception is the contract.
  - `assertThatThrownBy(...).isInstanceOf(...)` when an exception is the contract.
  - `assertThat(vectorStore.save(...)).isFalse()` when vector store is unavailable and graceful skip is expected.
- Align display names with the asserted contract.
- Do not edit `VectorStoreService` in this loop.

Verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=VectorStoreServiceErrorHandlingTest test
```

Expected proof:
- The test fails if the intended error-handling contract changes.
- No test passes by swallowing an exception.

### [bugfix:test-search-controller-conditional-pass]
Bug statement: `SearchControllerIntegrationTest.should_include_candidate_info_in_results` can pass without asserting anything when the response is not `200 OK`, the body is null, or the candidate list is empty.

Smallest failing path:
- `backend/cvect/src/test/java/com/walden/cvect/web/controller/search/SearchControllerIntegrationTest.java`
- `should_include_candidate_info_in_results`
- production path under test: `POST /api/search`

Why this is a test bug:
- The assertion block is guarded by nested `if` checks.
- If the API returns a non-OK status or an empty result, the test completes green.
- This is worse than a skipped test because the report suggests coverage exists.

Regression target:
- Either convert unavailable dependencies into an explicit `Assumptions.assumeTrue(...)` skip, or seed deterministic candidate/vector data and assert:
  - status is `200 OK`
  - body is not null
  - `candidates` is present
  - at least one candidate exists
  - the first candidate has `candidateId` and `score`
- Keep the fix inside this test class only.

Verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=SearchControllerIntegrationTest test
```

Expected proof:
- The test either honestly skips when pgvector is unavailable or fails when the API contract is not met.
- It must not pass through an empty assertion path.

### [bugfix:test-output-noise]
Bug statement: the test suite emits excessive SQL and debug stdout, making real failures harder to review.

Smallest failing path:
- `backend/cvect/src/test/resources/application.properties` has `spring.jpa.show-sql=true`.
- Several tests print directly with `System.out`, including `FullPipelineIntegrationTest`, `TikaResumeParserTest`, `ResumeChunkPipelineTest`, and `FactExtractionIntegrationTest`.

Why this is a test bug:
- `./mvnw -q test` produced very large output even though the suite passed.
- Excess output hides useful failure context and slows code review.

Regression target:
- Disable Hibernate SQL echo in test properties unless a specific test opts in.
- Remove direct `System.out` debug prints or guard them behind a local test logger.
- Do not weaken assertions.

Verification:

```bash
cd backend/cvect && ./mvnw -q test
```

Expected proof:
- Suite remains green.
- Output is materially smaller and no longer includes raw resume contents or repeated SQL.

## Verification
Run the narrowest relevant test first:

```bash
cd backend/cvect && ./mvnw -q -Dtest=ChangedTestClass test
```

If multiple touched test classes are tightly related:

```bash
cd backend/cvect && ./mvnw -q -Dtest=FirstChangedTest,SecondChangedTest test
```

If only test resources or shared test setup changed:

```bash
cd backend/cvect && ./mvnw -q test-compile
```

Run the full backend suite only if the touched test affects shared fixtures or cross-cutting test configuration:

```bash
cd backend/cvect && ./mvnw -q test
```

## Completion Report
Ralph must report:
- root cause or coverage gap
- smallest failing path
- files changed
- verification command and result
- whether the change stayed within `backend/cvect/src/test/**`
- remaining risk, especially any production bug that was exposed but not fixed due to test-only scope

When complete, print exactly:

```text
LOOP_COMPLETE
```
