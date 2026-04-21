# ExecPlan: Role-aware data scope and UI actions

## Goal
Make the three default roles manage recruitment data from different scopes instead of seeing the same operational surface. OWNER and HR_MANAGER can work across the tenant; RECRUITER can only work with JD/candidate data they created.

## Impact scope
- Backend schema: add JD creator ownership metadata.
- Backend authorization: apply role-aware data scope to JD list/detail/update/delete, candidate list/status updates, upload, resume parse, and semantic search candidate IDs.
- Auth seed data: keep the three roles and add demo users for HR_MANAGER and RECRUITER so behavior is visible locally.
- Frontend: show role context and hide/disable actions based on permissions.
- Tests: add focused controller/service tests for scope filtering and token/user metadata behavior where practical.

## Risks
- Existing JD rows need a safe backfill owner; use the default demo owner when present.
- Cached search must include user scope, not only tenant, or recruiter users may see cached tenant-wide results.
- Deleting/updating a JD must use the same data-scope check as listing.
- UI hiding is convenience only; backend remains the source of enforcement.

## Implementation steps
1. Add `created_by_user_id` to `job_descriptions`, entity/repository support, and create-time population.
2. Add a small `DataScopeService` to centralize role scope decisions.
3. Apply scope to JD, candidate, upload, resume parse, batch stream, batch detail, and semantic search paths.
4. Seed demo users for all roles and preserve existing role permission sets.
5. Add frontend permission helpers and role-specific action visibility.
6. Run targeted backend tests, frontend tests/build, then rebuild/restart local containers.

## Verification
- Backend targeted Maven tests for JD scope and semantic search scope.
- Frontend `npm test` and `npm run build`.
- Local HTTP smoke: login as demo / hr / recruiter and verify accessible scopes differ.

## Rollback
- Revert code changes and migration. Existing DB can keep nullable `created_by_user_id`; old code ignores it.

# ExecPlan: Role-specific frontend workspaces

## Goal
Refactor the Vue workspace so OWNER, HR_MANAGER, and RECRUITER see different operating contexts, visual emphasis, feature availability, and data-scope explanations while keeping backend authorization as the enforcement source.

## Impact scope
- Frontend `App.vue`: add role profiles, permission capability summaries, role-specific metrics, scoped copy, and owner-only audit visibility.
- Frontend styles: introduce role theme variables and a denser enterprise workspace layout without changing API contracts.
- Verification: run frontend tests/build and inspect the local served app after rebuilding/restarting the frontend container when practical.

## Risks
- UI role labels must not imply permissions that the backend does not grant.
- Recruiter must not see tenant-wide wording; OWNER/HR_MANAGER can share tenant-wide data scope but should still have distinct management emphasis.
- Owner-only audit UI should fail quietly if the audit endpoint has no data yet.

## Implementation steps
1. Add computed role profiles and permission capability metadata in `App.vue`.
2. Replace the generic header with a role-aware workspace header, metrics, and data-view strip.
3. Rename JD and candidate sections dynamically by role and keep action buttons permission-gated.
4. Add owner-only audit log preview behind `AUDIT_READ`.
5. Update CSS with role theme variables, responsive workspace layout, and tighter radii/spacing.

## Verification
- `cd frontend && npm test`
- `cd frontend && npm run build`
- If containers are running, rebuild/recreate the frontend service and browse `http://localhost:8088/`.

## Rollback
- Revert the frontend `App.vue` and `style.css` changes. Backend RBAC/data-scope behavior remains unchanged.
