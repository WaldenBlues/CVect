You are working in the repository root.

Do not rely on .ralph scratchpad or prior loop state.
If .ralph state files are missing or stale, ignore them.

Read and obey:
- ./AGENTS.md
- ./plan.md

Task:
Execute the bounded run described in `Current Ralph 3-4 Hour Run Window` inside `./plan.md`.

Run rules:
- Use the mandatory skills named by AGENTS.md: minimal_refactor and Spring-bugfix.
- Work only the primary items listed in the current run window, in order.
- Use the reserve item only when the run window explicitly allows it.
- Do not choose any other backlog item during this run.
- Keep the run within the recommended iteration ceiling of 32.
- For each selected item, do one focused bugfix cycle: restate the bug, identify the smallest failing path, add or update the narrowest regression test when practical, apply the smallest safe fix, run the smallest relevant verification, and record remaining risk.
- Preserve existing architecture, package structure, naming, Spring Boot conventions, bean wiring, validation, and transaction boundaries.
- Do not perform unrelated refactors, speculative abstractions, or broad cleanup.
- Before starting the first fix, record a wall-clock start time so the stop conditions can be evaluated.

Stop conditions:
- Stop after all primary items are completed, proven false, or explicitly blocked, unless the reserve rule in `plan.md` applies.
- Stop after the reserve item is completed, proven false, or explicitly blocked.
- Do not start a new item after 210 elapsed minutes.
- Do not start a new item if fewer than 30 minutes remain before the 4 hour hard cap.
- If the current item would require a broad refactor, new subsystem, schema/data migration, or unavailable external network dependency, record it as blocked and move to the next allowed item.
- If verification fails for an unrelated environment reason, report the exact failing command and use the smallest relevant available verification before deciding whether to continue.

Completion:
When stopping for any valid stop condition, summarize the completed, false-positive, blocked, and unattempted allowed items, then print exactly:
LOOP_COMPLETE
