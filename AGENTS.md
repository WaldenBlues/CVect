# CVect - Agent Guidelines

**Generated:** 2026-01-25
**Commit:** 69da5d4
**Branch:** main

## OVERVIEW
Vector-based CV + LLM project for resume parsing and fact extraction using Java 17 + Spring Boot.

## STRUCTURE
```
CVect/
├── backend/cvect/           # Java Spring Boot application
│   ├── src/main/java/       # Source code
│   ├── src/test/java/       # Tests (65 @Test methods)
│   └── src/main/resources/  # Configuration + test PDFs
├── ml/                      # Machine learning components
├── docs/                    # Design documentation
└── scripts/                 # Tooling scripts
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Main application | `backend/cvect/src/main/java/com/walden/cvect/CvectApplication.java` | Spring Boot entry point |
| REST endpoints | `backend/cvect/src/main/java/com/walden/cvect/web/controller/` | Resume parsing API |
| Business logic | `backend/cvect/src/main/java/com/walden/cvect/service/` | Service layer with interfaces |
| Data models | `backend/cvect/src/main/java/com/walden/cvect/model/` | Entities + fact extraction |
| PDF parsing | `backend/cvect/src/main/java/com/walden/cvect/infra/parser/` | Apache Tika integration |
| Vector storage | `backend/cvect/src/main/java/com/walden/cvect/infra/vector/` | Embedding + similarity search |
| Test files | `backend/cvect/src/main/resources/static/` | My.pdf (450KB), Resume.pdf (551KB) |

## CONVENTIONS
### Java Spring Boot Specific
- **Package structure**: `com.walden.cvect.{layer}.{domain}`
- **Entity annotation**: `@Entity` with `@Table` naming
- **Dependency injection**: Constructor injection preferred
- **Configuration**: H2 in-memory for dev, PostgreSQL for prod
- **Immutability**: Value objects use `final` fields, factory methods
- **Chinese comments**: Business logic explanations in Chinese

### Maven Dependencies
- Spring Boot 3.5.9 with Java 17
- Apache Tika 3.2.3 for PDF parsing
- Lombok for boilerplate reduction
- H2 + PostgreSQL for database
- WebFlux for HTTP client to embedding service

### Testing
- JUnit 5 with `@SpringBootTest` for integration tests
- Test tags: `@Tag("integration")`, `@Tag("pipeline")`, `@Tag("api")`
- Real PDF files (no mocking) for integration tests
- 65 test methods across multiple test classes

## ANTI-PATTERNS (THIS PROJECT)
- **NEVER** persist EXPERIENCE and SKILL chunks to database (vector-only)
- **NEVER** use mocking for PDF parsing integration tests
- **ALWAYS** use guard clauses for null safety
- **ALWAYS** provide Chinese comments for business logic

## UNIQUE STYLES
### Fact Extraction Architecture
```java
// Rule-based fact extraction with functional interfaces
ChunkFactRule rule = ctx -> FactDecision.accept("reason");
switch (chunk.getType()) {
    case CONTACT -> handleContact(candidateId, data);
    case EXPERIENCE -> { /* skip - vector only */ }
    // ...
}
```

### Value Object Pattern
```java
// Factory methods over constructors
FactDecision.accept(reason)  // not new FactDecision(...)
```

## COMMANDS
```bash
cd backend/cvect
./mvnw compile              # Compile source code
./mvnw test                 # Run all tests (65 methods)
./mvnw test -Dtest=ClassName # Single test class
./mvnw spring-boot:run       # Start application
```

## DOCKER COMPOSE
```bash
# Start PostgreSQL with pgvector
docker compose up -d postgres

# Build and run ML embedding service
docker build -t qwen-embedding:0.6b ./ml
docker run -p 8001:8001 qwen-embedding:0.6b

# Full stack (PostgreSQL + Backend + ML)
docker compose up -d  # When services are added to compose
```

## NOTES
- **Key Decision**: EXPERIENCE/SKILL chunks are vector-only, not persisted
- **Database**: H2 in-memory for development, PostgreSQL for production
- **Vector Search**: pgvector integration planned for similarity search
- **Test Files**: Use real PDFs from `static/` directory
- **Entry Point**: `CvectApplication.main()` with `@EnableJpaRepositories`