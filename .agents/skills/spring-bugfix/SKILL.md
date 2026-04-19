---
name: spring-bugfix
description: Use for Spring Boot bug fixes where minimal change, regression proof, and targeted verification are required. Do not use for large refactors or new features.
---

# Spring Boot Bug Fix Workflow

1. Restate the bug in one sentence.
2. Identify the smallest failing path.
3. Add or describe a regression test first when practical.
4. Fix only the necessary code path.
5. Preserve existing architecture and naming.
6. Run the smallest relevant verification.
7. Report:
   - root cause
   - files changed
   - proof the bug is fixed
   - remaining risks
