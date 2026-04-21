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

## Mandatory skills
Always apply these skills before editing:
- minimal_refactor
- Spring-bugfix

## Working rules
- Follow existing package structure and naming style.
- Prefer modifying existing services/controllers/tests over introducing new layers.
- Keep changes scoped to the requested behavior.
- No unrelated refactor or broad cleanup.
- Use the smallest safe diff.
- Reproduce -> isolate -> patch -> verify.
- Add or update a regression test when practical.
- Respect Spring Boot conventions and bean wiring.

## Verification
- Backend tests:
  - `cd backend/cvect && ./mvnw -q test`
- Backend compile check:
  - `cd backend/cvect && ./mvnw -q -DskipTests compile`
- Frontend tests:
  - `cd frontend && npm test`
- Frontend build:
  - `cd frontend && npm run build`

## Notes
- Report exact failing checks if broad suites fail for unrelated environment reasons.
- Call out risky schema or API compatibility impacts explicitly.
- For complex cross-module work, update `PLANS.md` before coding.