# CVect Backend - Maven Spring Boot Module

**Generated:** 2026-01-25
**Commit:** 69da5d4
**Branch:** main

## OVERVIEW
Spring Boot 3.5.9 + Java 17 module for resume parsing API with PostgreSQL/H2 + ML embedding.

## STRUCTURE
```
backend/cvect/
├── pom.xml               # Maven config (Spring Boot 3.5.9)
├── src/main/java/        # Source (com.walden.cvect)
├── src/test/java/        # Tests (65 @Test methods)
├── src/main/resources/   # Config + test PDFs
└── target/               # Build output
```

## BUILD & RUN
```bash
./mvnw compile           # Compile
./mvnw test              # Run all tests
./mvnw spring-boot:run   # Start on port 8080

# Profiles
SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run      # H2 (dev)
docker compose up -d postgres && ./mvnw spring-boot:run  # PostgreSQL
```

## CONFIGURATION
### Database Profiles
| Profile | Purpose | Config |
|---------|---------|--------|
| **Default** | PostgreSQL production | `application.yml` |
| **h2** | H2 in-memory (dev/test) | `application-h2.yml` |

### Key Components
- **Embedding service**: HTTP client to ML service (`localhost:8001`)
- **Vector storage**: PostgreSQL + pgvector (HNSW index)
- **PDF parsing**: Apache Tika 3.2.3 (no mocking in tests)

### Environment
```yaml
app.embedding.model-name=Qwen/Qwen2.5-Embedding-0.6B-Instruct
app.embedding.device=cpu  # or cuda
```

## TESTING
### Patterns
- **Integration tests**: `@SpringBootTest` with real PDFs
- **Test tags**: `@Tag("integration")`, `@Tag("pipeline")`, `@Tag("api")`
- **Database**: H2 in-memory (profile: h2)

### Commands
```bash
./mvnw test -Dtest="*IntegrationTest"
./mvnw test -Dtest="ClassName"
```

### Best Practices
#### Test Pyramid Strategy
- **Unit tests**: Isolate business logic with mocks (e.g., `SearchControllerTest`)
- **Integration tests**: Test cross-component interactions with `@SpringBootTest`
- **E2E tests**: Full pipeline tests with real PDFs (tagged `@Tag("pipeline")`)

#### Mocking Guidelines
- Use `@ExtendWith(MockitoExtension.class)` for unit tests
- Mock external dependencies: `EmbeddingService`, `VectorStoreService`
- Keep integration tests for database operations (H2 in-memory)
- Never mock PDF parsing in integration tests (use real PDFs from `static/`)

#### External Dependencies
- **PostgreSQL + pgvector**: Use Testcontainers for reliable integration tests (planned)
- **Python embedding service**: Mock HTTP client in unit tests, use real service in integration tests
- **Database**: Always use H2 profile (`spring.profiles.active=h2`) for tests

#### Validation & Error Handling
- Add `@Valid` annotation to controller methods
- Use Jakarta validation constraints (`@NotBlank`, `@NotNull`)
- Test error scenarios with mocked exceptions
- Ensure all API endpoints have validation tests

#### Test Tags
- `@Tag("integration")`: Requires Spring context
- `@Tag("pipeline")`: Full pipeline with PDF processing
- `@Tag("api")`: REST API endpoint tests
- `@Tag("vector")`: Vector storage operations

## CONVENTIONS
### Package Structure
- `com.walden.cvect.{layer}.{domain}` pattern
- **Infrastructure**: `infra/` (parser, vector, embedding, process)
- **Services**: Interfaces in `service/`, `*ServiceImpl` implementations
- **Web**: `web/controller/` for REST endpoints

### Configuration Priority
1. Command line args (`--server.port=8081`)
2. `application.properties` (overrides YAML)
3. `application.yml` (primary)
4. Profile files (`application-{profile}.yml`)

## ANTI-PATTERNS
- **NEVER** run tests with PostgreSQL (use H2 only)
- **NEVER** mock PDF parsing in integration tests
- **ALWAYS** use constructor injection (`@RequiredArgsConstructor`)
- **ALWAYS** check embedding service availability

## NOTES
- **ML Integration**: HTTP calls to `http://localhost:8001/embed`
- **Vector Search**: pgvector via Hibernate community dialects
- **PDF Storage**: Test PDFs in `static/` (My.pdf, Resume.pdf)
- **H2 Console**: `http://localhost:8080/h2-console` (h2 profile)