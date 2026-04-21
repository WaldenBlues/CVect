You are working in the repository root.

Do not rely on .ralph scratchpad or prior loop state.
If .ralph state files are missing or stale, ignore them.

Read and obey:
- ./AGENTS.md

Task:
Strengthen backend test coverage in `backend/cvect` by choosing one concrete weakly covered backend behavior and executing exactly one focused cycle:

1. inspect the relevant production code and existing tests
2. choose one meaningful missing or weakly covered behavior
3. add or improve one focused backend test
4. run the smallest relevant test command
5. if the test reveals a real bug, apply the smallest safe fix
6. rerun verification
7. summarize what was done, then stop

Constraints:
- Mandatory skills: minimal_refactor, Spring-bugfix
- No unrelated refactor
- No speculative abstraction
- Respect Spring Boot conventions, bean wiring, validation, and transaction boundaries
- Prefer regression tests and narrow verification first

Completion:
When the single focused cycle is complete, print exactly:
LOOP_COMPLETE