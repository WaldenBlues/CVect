---
name: maven-verify
description: Use when a Java/Maven repository needs targeted verification after code changes.
---

# Maven Verification

Choose the smallest sufficient check:
1. compile-only if structure changed but behavior did not
2. targeted tests if a specific module/class changed
3. full test suite only when necessary

Always report:
- command run
- result
- whether coverage is sufficient
