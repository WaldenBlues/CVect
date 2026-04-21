# Repository Instructions

## Tech Stack
- Java 17
- Spring Boot
- Maven wrapper
- JUnit 5
- PostgreSQL
- Vue 3 frontend
- FastAPI embedding service

## Repository Layout
- Backend Java project root: `backend/cvect`
- Frontend project root: `frontend`
- Embedding service root: `Qwen`
- Deployment entrypoint: `docker-compose.yml`

## Change Policy
- Follow existing package structure and naming style.
- Prefer modifying existing services/controllers/tests over introducing new layers.
- Avoid speculative abstraction.
- Keep changes scoped to the requested behavior.
- Do not refactor unrelated code.

## Verification Commands
- For backend unit-level changes, run:
  - `cd backend/cvect && ./mvnw -q test`
- For backend compile-only checks when tests are too broad:
  - `cd backend/cvect && ./mvnw -q -DskipTests compile`
- For frontend utility changes, run:
  - `cd frontend && npm test`
- For frontend build checks, run:
  - `cd frontend && npm run build`

## Testing Rules
- Bug fix => add or update a regression test first when practical.
- API change => include controller/service level verification.
- Do not change unrelated tests just to make the suite green.
- If a broad suite fails for unrelated environment reasons, report the exact failure and the narrower checks that passed.

## Review Rules
- Every changed line must map to the request.
- Call out any risky schema/API compatibility impact explicitly.
- Use `code_review.md` when reviewing changes.

## ExecPlans
- When implementing complex features, major refactors, or cross-module changes, first create or update an ExecPlan in `PLANS.md` before coding.
- The ExecPlan should cover goal, impact scope, risks, implementation steps, verification for each step, and rollback approach.

## Local Tool Debug Notes
- If `apply_patch` reports `No such file or directory` for a file that shell commands can read, first verify the default sandbox with `pwd`, `ls`, or `sed` without escalation.
- In WSL sessions, this can be a transient Codex sandbox/filesystem-view issue. Restarting Codex and restoring the conversation can restore both default shell access and `apply_patch`.
- After restart, confirm `apply_patch` with a harmless repo-local add/delete probe before editing real files.
- Do not treat an `apply_patch` path error as proof that the target file is missing; confirm with shell commands first.
- If the issue persists and the change is blocking, use a small scoped fallback edit, then verify with `git diff --check` and the smallest relevant test.
