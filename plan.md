# Ralph Spring Bugfix Scan Plan

## Goal
Use `spring-bugfix` to fix concrete defects found by the current code scan.

This plan is not a workflow-only checklist and not a broad refactor request. Each item below names a suspected bug, the smallest path to prove it, and the regression target Ralph should implement before or with the smallest safe fix.

## Scope
This file is the only Ralph plan file. Do not recreate `PLAN.md`, `PLANS.md`, or another parallel plan document.

Primary scope:
- `backend/cvect/src/main/java/**`
- `backend/cvect/src/test/java/**`
- `backend/cvect/src/main/resources/**`
- `backend/cvect/src/test/resources/**`

Only touch frontend code if the selected bug explicitly requires client/server contract alignment. Do not edit `ralph.yml`, `AGENTS.md`, `PROMPT.md`, unrelated scripts, generated files, or deployment files unless the selected security/config bug specifically calls for it.

## Working Rule
Process the backlog in priority order. Pick one bugfix at a time, write or update the narrowest regression test, apply the smallest production patch, then run the smallest relevant verification command. If a finding proves false, leave a short note in the completion report and move to the next item.

## Current Acceptance Baseline
Latest manual acceptance run:

```bash
cd backend/cvect && ./mvnw -q test
```

Observed on 2026-04-22:
- `190` tests
- `0` failures
- `0` errors
- `14` skipped

Latest Ralph loop observation:
- `.ralph/events-20260422-144619.jsonl` ended with `max_iterations`.
- `.ralph/agent/summary.md` reports `5` iterations, `18m 34s`, exit code `2`.
- No readable completion event was recorded for that loop, so do not treat the loop itself as a completed bugfix.

## Carry-Forward Risk
Index SQL compatibility is still mostly covered by Mockito and string assertions. If index compatibility becomes a high-risk path, add a real PostgreSQL + pgvector integration test that creates an incompatible index and verifies it is rebuilt with the configured index type, opclass, and dimension.

## Reviewed Bugfix Backlog

### P0 [bugfix:actuator-endpoints-public]
Bug statement: Security is enabled by default, but every non-`/api/**` request is permitted after `/actuator/health`, so sensitive actuator endpoints exposed in production config can be accessed without authentication.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/security/SecurityConfig.java:30`
- `backend/cvect/src/main/java/com/walden/cvect/security/SecurityConfig.java:34`
- `backend/cvect/src/main/resources/application.yml:139`
- `backend/cvect/src/main/resources/application.yml:143`
- `backend/cvect/src/main/resources/application.yml:146`

Smallest failing path:
- Start MVC security context with `app.security.enabled=true`.
- `GET /actuator/health` should remain public.
- `GET /actuator/env`, `/actuator/beans`, `/actuator/mappings`, `/actuator/loggers`, `/actuator/metrics`, and custom performance endpoints should not be public.

Regression target:
- Add a focused security test around `SecurityConfig`.
- Assert unauthenticated `/actuator/health` is allowed.
- Assert unauthenticated sensitive actuator endpoints return `401` or `403`.
- Assert an authenticated user without admin authority cannot access admin-grade actuator endpoints if Ralph chooses authority-based protection.

Minimal fix direction:
- Put an explicit `/actuator/health` permit rule before a restrictive `/actuator/**` rule.
- Prefer `hasAuthority(PermissionCodes.SYSTEM_ADMIN)` for non-health actuator endpoints if existing JWT authorities are available.
- Change health details from `always` to `when_authorized` or remove detailed public health output if the security test proves details leak.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=SecurityConfigTest test
```

### P0 [bugfix:unsafe-demo-auth-defaults]
Bug statement: Runtime security creates default users with a fixed password and JWT uses a hardcoded fallback secret when `CVECT_JWT_SECRET` is absent, so a deployed instance can be authenticated with known credentials and tokens signed by a known secret.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/security/AuthDataInitializer.java:55`
- `backend/cvect/src/main/java/com/walden/cvect/security/AuthDataInitializer.java:97`
- `backend/cvect/src/main/java/com/walden/cvect/security/JwtService.java:35`
- `backend/cvect/src/main/java/com/walden/cvect/security/JwtService.java:115`
- `backend/cvect/src/main/resources/application.yml:40`
- `docker-compose.yml:87` does not pass `CVECT_JWT_SECRET`.

Smallest failing path:
- With `app.security.enabled=true` and no explicit demo-user opt-in, the application should not create `demo/hr/recruiter` accounts with password `demo123`.
- With security enabled and a blank/default JWT secret, startup should fail fast or require an explicit development mode.

Regression target:
- Add a test for `AuthDataInitializer` showing demo users are disabled unless an explicit property enables them.
- Add a `JwtService` or configuration test showing blank/default secret is rejected when security is enabled.
- Keep test fixtures able to opt in to demo users or provide a test-only JWT secret.

Minimal fix direction:
- Gate demo users behind a property such as `app.security.demo-users.enabled=false` by default.
- Require `CVECT_JWT_SECRET` or reject `cvect-dev-secret-change-me` when `app.security.enabled=true`.
- If local demo deployment still needs defaults, make the opt-in explicit in `.env` or documentation rather than implicit in production config.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=JwtServiceTest,AuthDataInitializerTest test
```

### P1 [bugfix:query-token-auth-too-broad]
Bug statement: `JwtAuthenticationFilter` accepts `access_token` query parameters on every request, which makes JWTs easier to leak through URLs, logs, proxies, and referrers even though query-token auth is only needed for EventSource/SSE.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/security/JwtAuthenticationFilter.java:77`
- `frontend/src/App.vue:1211` sends query tokens only for SSE.
- SSE endpoints are `backend/cvect/src/main/java/com/walden/cvect/web/controller/candidate/CandidateStreamController.java:33` and `backend/cvect/src/main/java/com/walden/cvect/web/controller/upload/BatchSseController.java:48`.

Smallest failing path:
- An authenticated JWT in `?access_token=` currently authenticates non-SSE endpoints such as `/api/jds`.
- SSE endpoints still need to support query-token auth because native `EventSource` cannot set custom headers.

Regression target:
- Add a security/filter test proving query-token auth is ignored for normal JSON endpoints.
- Add a second test proving query-token auth still works for `/api/candidates/stream`, `/api/sse/batches/{batchId}`, and the legacy batch stream path.

Minimal fix direction:
- Restrict query-token extraction to known SSE paths.
- Keep `Authorization: Bearer` and cookie token behavior unchanged.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=JwtAuthenticationFilterTest,CandidateStreamControllerIntegrationTest,BatchSseControllerIntegrationTest test
```

### P1 [bugfix:vector-sse-false-ready-on-partial-failure]
Bug statement: The vector ingest worker publishes `VECTOR_DONE` with `noVectorChunk=false` when a candidate has at least one DONE task and no pending tasks, even if another vector ingest task for the same candidate has FAILED, causing the UI to temporarily mark partial vectorization as READY.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/service/vector/queue/VectorIngestQueueWorkerService.java:223`
- `backend/cvect/src/main/java/com/walden/cvect/service/vector/queue/VectorIngestQueueWorkerService.java:229`
- `backend/cvect/src/main/java/com/walden/cvect/service/vector/queue/VectorIngestQueueWorkerService.java:243`
- `backend/cvect/src/main/java/com/walden/cvect/web/controller/candidate/CandidateController.java:177`
- `frontend/src/App.vue:1252` currently treats every vector event as READY.

Smallest failing path:
- Candidate has one `VectorIngestTaskStatus.DONE` task and one `VectorIngestTaskStatus.FAILED` task.
- A later successful task calls `publishVectorDoneIfReady`.
- The event should not claim full readiness.

Regression target:
- Add or extend `VectorIngestQueueWorkerServiceTest`.
- Verify DONE + FAILED + no inflight does not publish a READY-style event.
- Verify all DONE + no failed/inflight still emits READY.
- If event contract changes, add/update the smallest frontend unit test or normalize handler test so the UI uses event status instead of forcing `READY`.
- Do not add more `CandidateController` vector-status-only tests for this item; recent Ralph loops already cover FAILED, PROCESSING, and PARTIAL list semantics.

Minimal fix direction:
- Check `FAILED` status before publishing vector readiness.
- Emit a status that matches `CandidateController.resolveVectorStatus` semantics, or suppress the event and let the existing candidate refresh polling load the authoritative state.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=VectorIngestQueueWorkerServiceTest test
```

### P1 [bugfix:search-only-ready-includes-partial-failed]
Bug statement: `onlyVectorReadyCandidates=true` can still return candidates that have both DONE and FAILED vector ingest tasks, even though the candidate list treats the same state as `PARTIAL` rather than ready.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/service/matching/SemanticSearchExecutionService.java:177`
- `backend/cvect/src/main/java/com/walden/cvect/service/matching/SemanticSearchExecutionService.java:180`
- `backend/cvect/src/main/java/com/walden/cvect/service/matching/SemanticSearchExecutionService.java:187`
- `backend/cvect/src/main/java/com/walden/cvect/web/controller/candidate/CandidateController.java:177`
- `backend/cvect/src/main/java/com/walden/cvect/web/controller/candidate/CandidateController.java:184`

Smallest failing path:
- Search returns a candidate match for a candidate with one `VectorIngestTaskStatus.DONE` task and one `VectorIngestTaskStatus.FAILED` task.
- Request sets `onlyVectorReadyCandidates=true`.
- Current filter includes the candidate because it only requires DONE and no PENDING/PROCESSING.

Regression target:
- Add or extend `SemanticSearchServiceTest` or `SemanticSearchExecutionServiceTest`.
- Verify DONE + FAILED + no inflight is excluded when `onlyVectorReadyCandidates=true`.
- Verify DONE-only remains included.
- Verify PENDING/PROCESSING remains excluded.
- Do not re-test `CandidateController.resolveVectorStatus` here unless the search fix changes that contract.

Minimal fix direction:
- Query failed task ids alongside inflight and done ids.
- Treat candidates with FAILED tasks as not ready unless the product explicitly defines DONE as overriding FAILED.
- Keep behavior aligned with `CandidateController.resolveVectorStatus`.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=SemanticSearchServiceTest test
```

### P1 [bugfix:vector-ingest-retries-nonretryable-failures]
Bug statement: `VectorIngestQueueWorkerService` detects transient embedding outages but does not use that signal for retry decisions, so permanent validation/configuration failures are retried until `maxAttempts` instead of failing fast.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/service/vector/queue/VectorIngestQueueWorkerService.java:187`
- `backend/cvect/src/main/java/com/walden/cvect/service/vector/queue/VectorIngestQueueWorkerService.java:188`
- `backend/cvect/src/main/java/com/walden/cvect/service/vector/queue/VectorIngestQueueWorkerService.java:191`
- `backend/cvect/src/main/java/com/walden/cvect/service/vector/queue/VectorIngestQueueWorkerService.java:195`

Smallest failing path:
- `vectorStoreService.save(...)` throws `IllegalArgumentException` for a non-retryable validation error.
- Current code records attempt `1` and puts the task back to `PENDING` when `maxAttempts > 1`.
- The task should become `FAILED` immediately for non-transient failures.

Regression target:
- Add `VectorIngestQueueWorkerServiceTest` cases:
  - connection-refused or timeout exception remains retryable until max attempts.
  - `IllegalArgumentException`, dimension mismatch, unsupported vector metric, or content validation failure becomes `FAILED` on first failure.

Minimal fix direction:
- Use `isTransientEmbeddingOutage(ex)` in the status decision.
- Retry only transient connectivity/timeouts.
- Fail fast for validation/configuration exceptions.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=VectorIngestQueueWorkerServiceTest test
```

### P1 [bugfix:nonfinite-jd-embedding-can-be-cached]
Bug statement: Chunk embeddings are now validated as finite, but job-description embeddings can still be cached with `NaN` or `Infinity` because `EmbeddingService` validates only dimension before `PersistedMatchScoreService` saves the vector.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/infra/embedding/EmbeddingService.java:217`
- `backend/cvect/src/main/java/com/walden/cvect/infra/embedding/EmbeddingService.java:225`
- `backend/cvect/src/main/java/com/walden/cvect/service/matching/PersistedMatchScoreService.java:217`
- `backend/cvect/src/main/java/com/walden/cvect/service/matching/PersistedMatchScoreService.java:223`
- `backend/cvect/src/main/java/com/walden/cvect/model/entity/FloatArrayTextConverter.java:22`

Smallest failing path:
- Mock `EmbeddingService.embed` or low-level embedding response conversion to return a vector containing `Float.NaN`.
- `PersistedMatchScoreService` currently can save that vector as `JobDescription.embedding`.
- Later vector scoring rejects the non-finite query embedding, causing repeated refresh failures.

Regression target:
- Add tests to `EmbeddingServiceConfigurationTest` or `PersistedMatchScoreServiceTest`.
- Assert non-finite embedding responses fail before persistence.
- Assert the job description embedding remains null and no candidate match scores are saved.

Minimal fix direction:
- Validate finite values in `EmbeddingService.validateVectorDimension` or add a shared embedding output validator.
- Ensure both native, OpenAI-compatible, and llama.cpp response paths use the same finite-value check.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=EmbeddingServiceConfigurationTest,PersistedMatchScoreServiceTest test
```

### P1 [bugfix:openai-embedding-response-order-can-mismatch-inputs]
Bug statement: OpenAI-compatible embedding responses can carry per-item indexes, but `EmbeddingService` ignores indexes and trusts response list order, so out-of-order response data can attach the wrong embedding vector to the wrong input text.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/infra/embedding/EmbeddingService.java:119`
- `backend/cvect/src/main/java/com/walden/cvect/infra/embedding/EmbeddingService.java:190`
- `backend/cvect/src/main/java/com/walden/cvect/infra/embedding/EmbeddingService.java:197`
- `backend/cvect/src/main/java/com/walden/cvect/infra/embedding/EmbeddingService.java:202`
- `backend/cvect/src/main/java/com/walden/cvect/infra/embedding/EmbeddingService.java:329`

Smallest failing path:
- Configure `app.embedding.api-format=openai` and batch two texts.
- Mock the OpenAI-compatible endpoint to return `data` entries in reverse index order.
- `embedBatch(List.of("alpha", "beta"))` should return alpha's vector first and beta's vector second, but current code returns response order.

Regression target:
- Extend `EmbeddingServiceHttpServerTest`.
- Include `index` in the mocked OpenAI response entries.
- Assert vectors are reordered by index before returning.
- Add a malformed response case for missing, duplicate, or out-of-range indexes if Ralph keeps strict validation small.

Minimal fix direction:
- Add `Integer index` to `OpenAiEmbeddingData`.
- In `validateAndConvertOpenAi`, sort or place vectors by index when all indexes are present.
- If indexes are absent, either preserve existing response-order behavior for compatibility or fail fast if strict OpenAI mode is configured.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=EmbeddingServiceHttpServerTest test
```

### P2 [bugfix:embedding-max-input-length-unused]
Bug statement: `app.embedding.max-input-length` is exposed as configuration but never applied before HTTP embedding requests, so oversized resume chunks or search queries can bypass the intended embedding input cap.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/infra/embedding/EmbeddingConfig.java:17`
- `backend/cvect/src/main/java/com/walden/cvect/infra/embedding/EmbeddingConfig.java:76`
- `backend/cvect/src/main/java/com/walden/cvect/infra/embedding/EmbeddingService.java:66`
- `backend/cvect/src/main/java/com/walden/cvect/infra/embedding/EmbeddingService.java:75`
- `backend/cvect/src/main/java/com/walden/cvect/infra/embedding/EmbeddingService.java:145`

Smallest failing path:
- Set `app.embedding.max-input-length=4`.
- Call `EmbeddingService.embedBatch(List.of("123456789"))`.
- Capture the HTTP request body and observe the full text is sent instead of a capped or rejected value.

Regression target:
- Add an `EmbeddingServiceHttpServerTest` case that captures native or OpenAI request payload.
- Assert text longer than the configured max length is either truncated deterministically or rejected with a clear exception before the HTTP call.
- Add a null/blank handling assertion if Ralph changes normalization.

Minimal fix direction:
- Apply the max length in one helper before batching and before all API formats.
- Prefer fail-fast for invalid configured limits and deterministic truncation or explicit `IllegalArgumentException` for oversized text; choose the behavior that matches existing API expectations.
- Ensure query embedding cache keys and sent text use the same normalized/capped value if truncation is chosen.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=EmbeddingServiceHttpServerTest,SearchCacheKeysTest test
```

### P2 [bugfix:vector-health-can-block-for-embedding-timeout]
Bug statement: `/api/vector/health` uses the embedding request timeout for health checks, so the health endpoint can block for the full embedding timeout, which defaults to 120 seconds in runtime configuration.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/web/controller/system/VectorHealthController.java:108`
- `backend/cvect/src/main/java/com/walden/cvect/web/controller/system/VectorHealthController.java:115`
- `backend/cvect/src/main/resources/application.yml:51`

Smallest failing path:
- Configure `app.embedding.timeout-seconds=120`.
- Make the embedding health URL hang or never respond.
- `/api/vector/health` should return a degraded response quickly instead of waiting for the full embedding inference timeout.

Regression target:
- Add or extend `VectorHealthControllerTest`.
- Verify the health check uses a short health-specific timeout or a bounded cap.
- Verify degraded response still includes the resolved health URL and error message.

Minimal fix direction:
- Introduce `app.embedding.health-timeout-seconds` or cap the health timeout to a small value such as 3 seconds.
- Prefer injecting a `WebClient.Builder` or testable collaborator if the current direct `WebClient.builder().build()` makes verification brittle.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=VectorHealthControllerTest test
```

### P2 [bugfix:search-controller-integration-conditional-pass]
Bug statement: `SearchControllerIntegrationTest.should_include_candidate_info_in_results` can pass without asserting anything when the response is not OK, the body is null, or the candidates list is empty.

Evidence:
- `backend/cvect/src/test/java/com/walden/cvect/web/controller/search/SearchControllerIntegrationTest.java:342`
- `backend/cvect/src/test/java/com/walden/cvect/web/controller/search/SearchControllerIntegrationTest.java:346`
- `backend/cvect/src/test/java/com/walden/cvect/web/controller/search/SearchControllerIntegrationTest.java:350`

Smallest failing path:
- Search endpoint returns `503`, null body, or empty candidates.
- Test still completes green.

Regression target:
- Seed deterministic candidate/vector data or explicitly skip when pgvector is unavailable.
- Assert status `200`, non-null body, non-empty `candidates`, and required candidate fields.
- Avoid nested conditional assertion blocks that allow a fake pass.

Minimal fix direction:
- Keep this as a test-only fix unless the strengthened test exposes a real production bug.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=SearchControllerIntegrationTest test
```

### P2 [bugfix:search-full-pipeline-silent-return]
Bug statement: `SearchControllerIntegrationTest.should_support_full_pipeline_to_search` returns early when parsed PDF chunks are empty, which records a passing test instead of an explicit skip or failure.

Evidence:
- `backend/cvect/src/test/java/com/walden/cvect/web/controller/search/SearchControllerIntegrationTest.java:286`
- `backend/cvect/src/test/java/com/walden/cvect/web/controller/search/SearchControllerIntegrationTest.java:290`

Smallest failing path:
- The sample PDF parser/chunker produces no EXPERIENCE or SKILL chunks.
- The test passes without proving vector storage or search behavior.

Regression target:
- Convert the early `return` to an explicit `Assumptions.assumeFalse(...)` skip if the sample input is optional, or replace the sample with deterministic text/PDF fixture that must produce vectorizable chunks.
- Assert each `vectorStore.save(...)` expected to persist a vector returns `true`.

Minimal fix direction:
- Keep the change inside `SearchControllerIntegrationTest` unless a deterministic shared fixture already exists.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=SearchControllerIntegrationTest test
```

### P2 [bugfix:cookie-auth-csrf-disabled]
Bug statement: Security authenticates same-origin API calls with an HttpOnly access-token cookie, but CSRF is disabled globally, so mutating endpoints rely on cookie `SameSite` behavior rather than an application-level CSRF or origin check.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/security/SecurityConfig.java:28`
- `backend/cvect/src/main/java/com/walden/cvect/web/controller/auth/AuthController.java:89`
- Mutating endpoints under `/api/**` use cookie authentication and include `POST`, `PUT`, `PATCH`, and `DELETE` handlers.

Smallest failing path:
- Login sets `CVECT_ACCESS_TOKEN` as an HttpOnly cookie.
- A same-site or cross-site request with that cookie can reach a mutating endpoint without a CSRF token or explicit trusted-origin check.
- The request should be rejected unless it carries the expected CSRF signal or passes a narrow origin policy.

Regression target:
- Add a focused Spring Security MVC test around one low-cost mutating endpoint such as `POST /api/search` or `PATCH /api/candidates/{id}/recruitment-status`.
- Assert cookie-authenticated mutation without CSRF/origin proof is rejected.
- Assert the frontend-supported valid request path remains accepted.

Minimal fix direction:
- Prefer the smallest Spring Security-compatible protection that fits the existing cookie model.
- If enabling Spring CSRF is too broad, add a narrow origin/referer guard for unsafe HTTP methods while preserving bearer-token API behavior.
- Keep public health and auth login/logout behavior explicit.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=SecurityConfigTest,AuthControllerTest test
```

### P2 [bugfix:auth-cookie-secure-attributes-missing]
Bug statement: Login and logout cookies do not set `Secure` from deployment configuration, and logout uses a servlet `Cookie` with fewer attributes than the login `ResponseCookie`, which can weaken production cookie handling and make cookie clearing inconsistent across browsers.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/web/controller/auth/AuthController.java:86`
- `backend/cvect/src/main/java/com/walden/cvect/security/JwtAuthenticationFilter.java:98`
- `backend/cvect/src/main/java/com/walden/cvect/security/JwtAuthenticationFilter.java:101`

Smallest failing path:
- In an HTTPS deployment, `POST /api/auth/login` sets `CVECT_ACCESS_TOKEN` without `Secure`.
- `POST /api/auth/logout` clears the same cookie with a different attribute set.
- Cookie creation and clearing should use one shared policy.

Regression target:
- Add or extend `AuthControllerTest` and `JwtAuthenticationFilterTest`.
- Assert login cookie has `HttpOnly`, `Path=/`, configured `SameSite`, configured `Secure`, and expected max age.
- Assert logout emits a clearing cookie with the same path/security policy and `Max-Age=0`.

Minimal fix direction:
- Introduce minimal cookie properties under `app.security.cookie.*`.
- Use one helper for login and logout cookie construction.
- Keep local development default compatible, but make production secure cookie opt-in explicit through configuration.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=AuthControllerTest,JwtAuthenticationFilterTest test
```

### P3 [bugfix:test-suite-output-noise-hides-failures]
Bug statement: The backend test suite is green, but `./mvnw -q test` still emits very large SQL/debug output, making Ralph loop failures hard to review and increasing the chance that meaningful failure context is missed.

Evidence:
- `backend/cvect/src/test/resources/application.properties:7`
- `backend/cvect/src/test/java/com/walden/cvect/FullPipelineIntegrationTest.java:58`
- `backend/cvect/src/test/java/com/walden/cvect/infra/parser/TikaResumeParserTest.java:45`
- `backend/cvect/src/test/java/com/walden/cvect/model/fact/FactExtractionIntegrationTest.java:78`
- `backend/cvect/src/test/java/com/walden/cvect/service/resume/ResumeChunkPipelineTest.java:82`

Smallest failing path:
- Run `cd backend/cvect && ./mvnw -q test`.
- The suite passes but prints thousands of lines of Hibernate SQL and direct `System.out` debug output.
- A quiet test suite should show failures and intentional summaries only.

Regression target:
- Disable Hibernate SQL echo in shared test properties unless a specific test opts in.
- Remove direct `System.out` debug prints or guard them behind a test logger.
- Keep assertions unchanged and do not hide failure diagnostics.

Minimal fix direction:
- Set `spring.jpa.show-sql=false` in test defaults.
- Replace ad hoc stdout dumps with assertions or logger statements that are quiet at the default test log level.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q test
```

## Execution Template For Each Ralph Loop
For the selected bug, Ralph must report:
- Bug statement.
- Root cause.
- Smallest failing path.
- Files changed.
- Regression test added or updated.
- Verification command and result.
- Remaining risk.

When the selected bugfix is complete, print exactly:

```text
LOOP_COMPLETE
```
