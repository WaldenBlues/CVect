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

## Current Ralph 3-4 Hour Run Window
Target: execute a bounded Ralph run of roughly `3` to `4` hours, with reserve items available if the primary items finish early.

Run only these primary items for the next Ralph execution, in this order:
- `P2 [bugfix:qwen-readiness-does-not-prove-embedding-loadable]` estimated `45-60` minutes.
- `P2 [bugfix:local-run-ignores-env-security-and-embedding-settings]` estimated `45-60` minutes.
- `P3 [bugfix:upload-worker-enabled-not-env-configurable]` estimated `25-40` minutes.
- `P3 [bugfix:docker-runtime-root-and-basic-auth-secret-hardening]` estimated `60-75` minutes.

Primary estimate: `175-235` minutes before normal verification overhead.

Recommended Ralph iteration ceiling: `32`.

Reserve items, in order:
- `P3 [bugfix:test-suite-output-noise-hides-failures]` estimated `30-45` minutes.
- `P3 [bugfix:web-log-control-characters-can-forge-lines]` estimated `25-40` minutes.
- `P3 [bugfix:qwen-cors-wildcard-with-credentials]` estimated `20-35` minutes.
- `P3 [bugfix:qwen-embed-error-leaks-internal-detail]` estimated `20-30` minutes.
- `P3 [bugfix:worker-executor-max-pool-can-be-less-than-core]` estimated `25-40` minutes.

Use reserve items one at a time only if all primary items finish before `190` elapsed minutes and at least `40` minutes remain before the `4` hour hard cap. Do not select any other backlog item in this run.

Stop conditions:
- Stop after all primary items are completed, proven false, or explicitly blocked, unless the reserve rule above applies.
- Stop after the allowed reserve items are completed, proven false, explicitly blocked, or no longer allowed by the elapsed-time rules.
- Do not start a new item after `210` elapsed minutes.
- Do not start a new item if fewer than `30` minutes remain before the `4` hour hard cap.
- If an item needs a broad refactor, new subsystem, schema/data migration, or unavailable external network dependency, record it as blocked and move to the next allowed item.
- If verification fails for an unrelated environment reason, report the exact failing command and decide based on the smallest relevant verification that is still available.
- When stopping for any condition above, print exactly `LOOP_COMPLETE`.

## Current Acceptance Baseline
Latest manual acceptance runs:

```bash
cd backend/cvect && ./mvnw -q test
cd frontend && npm test
```

Observed on 2026-04-23:
- Backend: `190` tests, `0` failures, `0` errors, `0` skipped.
- Frontend: `2` tests, `0` failures, `0` errors.
- First backend attempt in the default sandbox was blocked by read-only Maven local repository metadata under `~/.m2`; rerun with Maven local repository write access passed.

Previous backend observation on 2026-04-22:
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

### P2 [bugfix:qwen-readiness-does-not-prove-embedding-loadable]
Estimated Ralph effort: 45-60 minutes.

Bug statement: Deployment health checks can report Qwen and backend vector health as healthy while the embedding model is not loaded and may not be loadable, so compose startup and smoke tests can pass before the first real embedding request fails.

Evidence:
- `Qwen/embedding_service.py:339` only loads the model when `PRELOAD_MODELS=true`.
- `Qwen/embedding_service.py:374` returns `/health` with `status=healthy` without checking model loadability.
- `Qwen/embedding_service.py:388` returns `/ready` with `status=ready` without requiring `embedding_loaded=true`.
- `Qwen/Dockerfile:49` defaults `PRELOAD_MODELS=false`.
- `docker-compose.yml:64` checks `http://127.0.0.1:8001/health`.
- `docker-compose.yml:90` points backend vector health at `http://qwen:8001/health` by default.
- `scripts/cloud-smoke-test.sh:72` only checks backend `/api/vector/health` for `"status":"UP"`.

Smallest failing path:
- Start Qwen with `PRELOAD_MODELS=false` and `HF_LOCAL_FILES_ONLY=true` with no cached model.
- `GET /health` and `GET /ready` still return success before any model can be loaded.
- Backend `/api/vector/health` and the cloud smoke test can pass even though the first `/embed` request fails during lazy model load.

Regression target:
- Add a focused Qwen service test or smoke-level script test.
- Assert `/health` remains a liveness endpoint and does not claim model readiness.
- Assert `/ready` is not successful, or returns a non-ready status, when the embedding model is not loaded or failed to load.
- Assert compose/backend health wiring uses readiness semantics for embedding-dependent checks.

Minimal fix direction:
- Keep `/health` as cheap process liveness.
- Make `/ready` truthfully reflect embedding readiness, either by requiring a loaded model or by reporting a non-ready status when lazy loading has not succeeded.
- Point compose and backend vector health defaults at the readiness endpoint if the check is meant to prove embedding availability.
- Update `scripts/cloud-smoke-test.sh` expectations so it cannot pass on liveness alone.

Suggested verification:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m py_compile Qwen/embedding_service.py
cd backend/cvect && ./mvnw -q -Dtest=VectorHealthControllerTest test
```

### P2 [bugfix:local-run-ignores-env-security-and-embedding-settings]
Estimated Ralph effort: 45-60 minutes.

Bug statement: `scripts/local-run.sh` is documented as using the same `.env` configuration source as server mode, but it starts backend and Qwen with a small hardcoded environment subset, so local acceptance can silently diverge from Docker/server behavior.

Evidence:
- `README.md:16` says there is one configuration file: `.env`.
- `README.md:70` calls `.env` the single config source.
- `README.md:108` says server mode reads the same `.env`.
- `scripts/local-run.sh:93` uses `CVECT_EMBED_HEALTH_URL` with a default `/health` URL instead of the backend-style `CVECT_EMBEDDING_HEALTH_URL`.
- `scripts/local-run.sh:225` starts Qwen.
- `scripts/local-run.sh:231` forces `HOST`, `PORT`, and `PRELOAD_MODELS=false` only.
- `scripts/local-run.sh:238` starts backend.
- `scripts/local-run.sh:245` through `scripts/local-run.sh:250` pass DB, port, and upload limits only.

Smallest failing path:
- Set security or embedding values in `.env`, for example `CVECT_JWT_SECRET`, `CVECT_SECURITY_ENABLED`, `CVECT_EMBEDDING_MAX_INPUT_LENGTH`, `CVECT_HF_LOCAL_FILES_ONLY`, or `CVECT_CACHE_ENABLED`.
- Run `scripts/local-run.sh start`.
- Backend or Qwen starts with defaults that do not match the documented `.env` runtime configuration, so a local test can pass while the server deployment behaves differently.

Regression target:
- Add a script-level test or dry-run harness that stubs process launch and captures the environment passed to backend and Qwen.
- Assert security, cache, vector, embedding model, HF offline/cache, concurrency, and max input settings are propagated consistently.
- Assert local health URL naming matches the backend/deployment convention.

Minimal fix direction:
- Reuse `resolve_setting` for the runtime properties already present in compose and application config.
- Pass the smallest necessary `.env`-derived variable set to backend and Qwen rather than forcing hardcoded local defaults.
- Keep developer-friendly defaults only when `.env` omits a value.

Suggested verification:

```bash
bash -n scripts/local-run.sh
cd backend/cvect && ./mvnw -q -DskipTests compile
```

### P3 [bugfix:upload-worker-enabled-not-env-configurable]
Estimated Ralph effort: 25-40 minutes.

Bug statement: Upload worker beans are conditional on `app.upload.worker.enabled`, and tests disable them directly with Spring properties, but runtime configuration hardcodes the property to `true`, so operators cannot disable the upload worker through `.env` the way they can disable the vector ingest worker.

Evidence:
- `backend/cvect/src/main/resources/application.yml:92` defines upload worker config.
- `backend/cvect/src/main/resources/application.yml:93` hardcodes `enabled: true`.
- `backend/cvect/src/main/java/com/walden/cvect/service/upload/queue/UploadQueueWorkerService.java:42` is conditional on `app.upload.worker.enabled`.
- `backend/cvect/src/main/java/com/walden/cvect/service/upload/queue/UploadQueueWorkerRunner.java:22` is conditional on `app.upload.worker.enabled`.
- `docker-compose.yml:92` exposes `CVECT_VECTOR_INGEST_WORKER_ENABLED`, but there is no matching upload worker environment override.

Smallest failing path:
- Set `CVECT_UPLOAD_WORKER_ENABLED=false` for a runtime deployment.
- Start the backend.
- Upload queue worker beans still load because `application.yml` does not bind that environment variable.

Regression target:
- Add or update a focused Spring context/configuration test.
- Assert `CVECT_UPLOAD_WORKER_ENABLED=false` or `app.upload.worker.enabled=false` prevents worker beans from loading.
- Assert the default remains enabled for current local behavior.

Minimal fix direction:
- Change `app.upload.worker.enabled` to `${CVECT_UPLOAD_WORKER_ENABLED:true}`.
- Add the environment variable to compose and local-run only if the selected fix includes deployment parity.
- Do not change worker scheduling, queue claim logic, or upload controller behavior.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=UploadQueueWorkerServiceIntegrationTest test
```

### P3 [bugfix:upload-total-byte-cap-not-env-configurable]
Estimated Ralph effort: 20-30 minutes.

Bug statement: ZIP total-byte cap is now enforced in `UploadApplicationService`, but the runtime config never exposes `CVECT_UPLOAD_MAX_TOTAL_BYTES`, so `.env`-based deployments cannot tune the limit the same way they tune the other upload caps.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/service/upload/UploadApplicationService.java:77` reads `app.upload.max-total-bytes` with a hardcoded fallback.
- `backend/cvect/src/main/java/com/walden/cvect/service/upload/UploadApplicationService.java:87` normalizes the fallback into the service state.
- `backend/cvect/src/main/resources/application.yml:90` through `backend/cvect/src/main/resources/application.yml:91` define upload caps but omit total bytes.
- `docker-compose.yml:92` through `docker-compose.yml:100` pass upload worker and storage settings but no total-byte cap.
- `scripts/local-run.sh:238` through `scripts/local-run.sh:250` also omit a total-byte cap override.

Smallest failing path:
- Set `CVECT_UPLOAD_MAX_TOTAL_BYTES=1` in `.env` or the shell environment.
- Start local or server mode.
- ZIP uploads still use the built-in 200 MB default because the new cap is not wired through the project's `CVECT_` configuration convention.

Regression target:
- Add a focused config test showing `CVECT_UPLOAD_MAX_TOTAL_BYTES` is honored.
- Add runtime wiring in the local and compose launch paths.
- Keep the default at 200 MB for existing behavior.

Minimal fix direction:
- Bind `app.upload.max-total-bytes` from `CVECT_UPLOAD_MAX_TOTAL_BYTES` in `application.yml`.
- Pass the variable through `docker-compose.yml` and `scripts/local-run.sh`.
- Leave the per-entry cap and ZIP truncation behavior unchanged.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=UploadControllerStorageIdempotencyIntegrationTest test
```

### P3 [bugfix:docker-runtime-root-and-basic-auth-secret-hardening]
Estimated Ralph effort: 60-75 minutes.

Bug statement: Runtime images do not declare least-privilege users, and nginx basic-auth password generation passes the password on the command line, increasing the blast radius of a container compromise and briefly exposing credentials through process arguments.

Evidence:
- `backend/cvect/Dockerfile:34` starts the runtime image without a later `USER` directive.
- `backend/cvect/Dockerfile:53` runs the Java process as the image default user.
- `Qwen/Dockerfile:55` runs the embedding service as the image default user.
- `docker-compose.yml:34` and `docker-compose.yml:60` mount Hugging Face cache paths under `/root/.cache/huggingface`.
- `frontend/Dockerfile:25` starts the nginx runtime image without a repository-level least-privilege adjustment.
- `frontend/docker-entrypoint.d/30-cvect-auth.envsh:12` passes `CVECT_BASIC_AUTH_PASSWORD` to `htpasswd -bc` as a process argument.

Smallest failing path:
- Build or inspect the runtime images.
- Backend, Qwen, and frontend containers run with default root-oriented runtime assumptions.
- Enabling frontend basic auth briefly places the basic-auth password in the `htpasswd` command arguments.

Regression target:
- Add static Dockerfile/entrypoint checks or document an image-inspection smoke test.
- Assert backend and Qwen runtime stages define non-root users and writable app/cache/storage directories.
- Assert nginx basic-auth generation reads the password from stdin or another non-argv path.

Minimal fix direction:
- Add dedicated non-root runtime users and ownership for `/app`, `/data/storage`, and model cache directories.
- Move Qwen HF cache away from `/root` or chown the mounted path for the service user.
- Replace `htpasswd -bc user password` with a form that avoids exposing the password in argv.
- Account for nginx port and writable temp/cache paths before changing the frontend runtime user.

Suggested verification:

```bash
docker compose --env-file .env -f docker-compose.yml config
bash -n frontend/docker-entrypoint.d/30-cvect-auth.envsh
```

### P3 [bugfix:web-log-control-characters-can-forge-lines]
Estimated Ralph effort: 25-40 minutes.

Bug statement: Structured web log formatting quotes strings but does not escape control characters such as CR/LF, so request paths, exception messages, or multipart filenames containing newlines can forge extra log lines.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/logging/support/WebLogFormatter.java:49` quotes string values.
- `backend/cvect/src/main/java/com/walden/cvect/logging/support/WebLogFormatter.java:50` through `backend/cvect/src/main/java/com/walden/cvect/logging/support/WebLogFormatter.java:51` escape backslash and double quote only.
- `backend/cvect/src/main/java/com/walden/cvect/logging/support/LogValueSanitizer.java:182` includes multipart filenames in summaries.
- `backend/cvect/src/main/java/com/walden/cvect/logging/support/LogValueSanitizer.java:197` through `backend/cvect/src/main/java/com/walden/cvect/logging/support/LogValueSanitizer.java:201` join original multipart filenames into one log value.
- `backend/cvect/src/main/java/com/walden/cvect/logging/support/LogValueSanitizer.java:246` through `backend/cvect/src/main/java/com/walden/cvect/logging/support/LogValueSanitizer.java:249` also escape backslash and double quote only.

Smallest failing path:
- Format a web log field or multipart filename containing `\nstatus=200 event=fake`.
- The formatted log output contains an actual newline, not an escaped sequence.
- Downstream log review can interpret the injected line as a separate structured event.

Regression target:
- Add focused unit tests for `WebLogFormatter` and `LogValueSanitizer`.
- Assert CR, LF, tab, and other ISO control characters are escaped or replaced.
- Assert normal spaces and existing quote/backslash escaping still behave as expected.

Minimal fix direction:
- Centralize a small control-character escaping helper.
- Escape `\r`, `\n`, and `\t` at minimum; preferably escape other ISO control characters as `\u00XX`.
- Keep log field names and existing output shape stable.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=WebLogFormatterTest,LogValueSanitizerTest test
```

### P3 [bugfix:qwen-cors-wildcard-with-credentials]
Estimated Ralph effort: 20-35 minutes.

Bug statement: Qwen embedding service installs permissive CORS with wildcard origins and credentials enabled, which is broader than needed for an internal embedding service and can produce unsafe or browser-incompatible CORS behavior if exposed.

Evidence:
- `Qwen/embedding_service.py:327` installs `CORSMiddleware`.
- `Qwen/embedding_service.py:329` sets `allow_origins=["*"]`.
- `Qwen/embedding_service.py:330` sets `allow_credentials=True`.
- `docker-compose.yml:37` through `docker-compose.yml:61` configure Qwen as a backend service, not a browser-facing API.

Smallest failing path:
- Send a browser preflight with an arbitrary `Origin`.
- Qwen accepts the origin policy even though embedding calls should be internal.
- If credentials are involved, wildcard-origin behavior can be incompatible with browser CORS rules or unintentionally permissive.

Regression target:
- Add a small FastAPI/TestClient test or a documented script-level smoke test.
- Assert default CORS does not allow arbitrary browser origins with credentials.
- Assert explicitly configured origins are honored if a deployment needs browser access.

Minimal fix direction:
- Introduce a minimal `CORS_ALLOW_ORIGINS` environment setting for Qwen.
- Default to no browser origins or a local-only list, not wildcard with credentials.
- Keep backend-to-Qwen server-side calls unaffected because they do not depend on browser CORS.

Suggested verification:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m py_compile Qwen/embedding_service.py
```

### P3 [bugfix:qwen-embed-error-leaks-internal-detail]
Estimated Ralph effort: 20-30 minutes.

Bug statement: Qwen `/embed` returns `str(exc)` in the HTTP 500 response, so model-load paths, dependency errors, proxy details, or other internal exception text can be exposed to clients.

Evidence:
- `Qwen/embedding_service.py:452` calls `_embedding_forward`.
- `Qwen/embedding_service.py:453` catches any exception.
- `Qwen/embedding_service.py:454` logs the detailed exception server-side.
- `Qwen/embedding_service.py:455` returns `detail=str(exc)` to the HTTP client.

Smallest failing path:
- Make `_embedding_forward` raise an exception containing a local path, model cache path, or upstream error text.
- `POST /embed` returns that internal message in the JSON error detail.
- A caller sees operational details that should remain in server logs.

Regression target:
- Add a focused Qwen API test that monkeypatches `_embedding_forward` to raise a sensitive message.
- Assert the response uses a generic client-facing error.
- Assert server-side logging still keeps the detailed exception for diagnosis.

Minimal fix direction:
- Return a generic `Embedding request failed` detail or a stable error code.
- Keep `logger.exception("Embedding failed")` for internal diagnostics.
- Do not hide validation errors such as batch-size violations.

Suggested verification:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m py_compile Qwen/embedding_service.py
```

### P3 [bugfix:worker-executor-max-pool-can-be-less-than-core]
Estimated Ralph effort: 25-40 minutes.

Bug statement: Upload and vector worker executor configs clamp the core pool size to at least `1`, but compute max pool size from the raw configured values, so invalid low values can still produce `maxPoolSize < corePoolSize` during startup.

Evidence:
- `backend/cvect/src/main/java/com/walden/cvect/config/UploadWorkerExecutorConfig.java:21` clamps upload core pool size to at least `1`.
- `backend/cvect/src/main/java/com/walden/cvect/config/UploadWorkerExecutorConfig.java:22` sets upload max pool size from raw `corePoolSize` and `maxPoolSize`.
- `backend/cvect/src/main/java/com/walden/cvect/config/VectorIngestWorkerExecutorConfig.java:21` clamps vector core pool size to at least `1`.
- `backend/cvect/src/main/java/com/walden/cvect/config/VectorIngestWorkerExecutorConfig.java:22` sets vector max pool size from raw `corePoolSize` and `maxPoolSize`.

Smallest failing path:
- Configure `app.upload.worker.executor.core-pool-size=0` and `app.upload.worker.executor.max-pool-size=0`.
- Or configure `app.vector.ingest.worker.executor.core-pool-size=0` and `app.vector.ingest.worker.executor.max-pool-size=0`.
- The executor attempts to initialize with core `1` and max `0`, causing startup failure or invalid executor state.

Regression target:
- Add focused config unit tests for both executor config classes.
- Assert zero or negative core/max values normalize to a valid executor.
- Assert normal positive values are preserved.

Minimal fix direction:
- Compute `normalizedCore = Math.max(1, corePoolSize)`.
- Compute `normalizedMax = Math.max(normalizedCore, maxPoolSize)`.
- Use normalized values consistently for both upload and vector executors.

Suggested verification:

```bash
cd backend/cvect && ./mvnw -q -Dtest=UploadWorkerExecutorConfigTest,VectorIngestWorkerExecutorConfigTest test
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
