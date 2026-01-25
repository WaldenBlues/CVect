# Fact Extraction Module

**Generated:** 2026-01-25
**Commit:** 69da5d4
**Branch:** main

## OVERVIEW
Rule-based fact extraction engine for resume parsing with functional interfaces.

## STRUCTURE
```
model/fact/
├── extract/              # 7 files - Extractor implementations
├── rules/               # 6 files - Business rule definitions
└── ChunkFactRule.java   # Core functional interface
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Core interface | `ChunkFactRule.java` | Functional interface for fact rules |
| Fact decisions | `extract/` | Contact, Education, Honor extractors |
| Business rules | `rules/` | Domain-specific rule implementations |
| Context objects | `ChunkFactContext.java` | Rule evaluation context |

## CONVENTIONS
### Functional Interface Pattern
```java
ChunkFactRule rule = ctx -> FactDecision.accept("reason");
// Return FactDecision.accept() or FactDecision.reject()
```

### Factory Methods Only
```java
FactDecision.accept(reason)    // NOT new FactDecision(...)
FactDecision.reject(reason)
```

### Vector-Only Skip Pattern
```java
case EXPERIENCE -> { /* skip - vector only */ }
case SKILL -> { /* skip - vector only */ }
```

## ANTI-PATTERNS (THIS MODULE)
- **NEVER** persist EXPERIENCE/SKILL chunks (vector-only design)
- **NEVER** use constructors for FactDecision objects
- **ALWAYS** provide Chinese comments for rule logic
- **ALWAYS** return FactDecision from rule functions

## NOTES
- EXPERIENCE/SKILL chunks are skipped intentionally for vector processing
- All business logic rules use Chinese comments for clarity
- Functional interface enables concise rule definitions
- Context objects carry chunk data for rule evaluation