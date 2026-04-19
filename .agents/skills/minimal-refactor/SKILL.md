---
name: minimal-refactor
description: Use for small refactors that improve readability or duplication without changing architecture, APIs, or broad behavior.
---

# Minimal Refactor Rules

- No new abstraction for single-use code.
- No package moves unless required.
- No public API changes unless requested.
- Prefer rename/extract-inline/small method cleanup.
- Verify behavior before and after.
