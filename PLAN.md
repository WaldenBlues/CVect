# Ralph Loop Bugfix Plan

## Goal
Fix the concrete backend bugs exposed by the current test changes. This loop is not a generic test-hardening pass: every test must prove or lock a specific bugfix, then the implementation must apply the smallest safe patch.

## Bugs To Fix
- `app.vector.index-type=ivfflat` is only partially honored. `createHnswIndex()` can now emit `ivfflat` SQL, but `ensureIndexCompatibility()` still checks only vector dimension, so an existing `hnsw` index can survive an `ivfflat` config change.
- `/api/search/admin/create-index` still returns `HNSW index created successfully` even when the configured index type is `ivfflat`.
- `filterByExperience=false` and `filterBySkill=false` must have an explicit, tested semantic-search meaning. Treat this as "search all chunk types", use balanced Experience/Skill weights, and preserve fallback scoring for `OTHER` chunks.
- `VectorStoreService.save(..., null)` must fail as validation before embedding or persistence work, with a clear `IllegalArgumentException`.

## Required Implementation
- Keep the public method name `createHnswIndex()` for compatibility, but make its behavior and returned/admin-visible messaging reflect the resolved index type.
- Extend vector index compatibility checks to include index type, metric opclass, and dimension. Rebuild the index when any of these differ from current config.
- Keep the `SearchWeightNormalizer` change only if service-level tests prove the no-filter search still ranks all chunk types correctly, including `OTHER` fallback scoring.
- Do not change repository instructions, Ralph config, frontend code, schema, package structure, or unrelated tests.

## Regression Tests First
- Add or update `VectorStoreServiceIndexTypeTest` to cover:
  - `ivfflat` creates `USING ivfflat` SQL without HNSW-only options.
  - default `hnsw` still creates `USING hnsw` SQL with HNSW options.
  - compatibility check rebuilds when an existing index definition has the wrong index type or opclass, not only the wrong dimension.
- Add or update `SearchControllerTest` or the narrowest controller-level test to verify the admin create-index response names the actual configured index type.
- Add or update `SemanticSearchServiceTest` to verify no-filter search passes `null` chunk type filters, searches all chunk types, and uses fallback max score when only `OTHER` chunks match.
- Strengthen `VectorStoreServiceSearchScoringTest` so `save(null)` asserts the exception type and message, and verifies no embedding call is made.

## Verification Commands
Run the smallest relevant check first:

```bash
cd backend/cvect && ./mvnw -q -Dtest=VectorStoreServiceIndexTypeTest,VectorStoreServiceSearchScoringTest,SearchWeightNormalizerTest,SearchCacheKeysTest,SemanticSearchServiceTest,SearchControllerTest test
```

Then run compile after the targeted tests pass:

```bash
cd backend/cvect && ./mvnw -q -DskipTests compile
```

If a broad check fails for environment reasons, report the exact failure and the narrower passing command.

## Completion Rules
- Summarize root cause, files changed, tests run, and remaining risk.
- Stop after this bugfix set; do not choose a new weakly covered behavior.
- Print exactly `LOOP_COMPLETE` as the final completion marker.
