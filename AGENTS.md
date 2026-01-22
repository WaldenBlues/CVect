# CVect - Agent Guidelines

## Project Overview
Vector-based CV + LLM project for resume parsing and fact extraction.
- **Backend**: Java 17 + Spring Boot 3.5.9 (Maven)
- **Location**: `backend/cvect/`

---

## Build, Test, and Lint Commands

### Build
```bash
cd backend/cvect
./mvnw compile          # Compile source code
./mvnw clean compile    # Clean and compile
./mvnw package          # Build JAR
```

### Test
```bash
cd backend/cvect
./mvnw test            # Run all tests
./mvnw test -Dtest=ClassName                # Single test class
./mvnw test -Dtest=ClassName#methodName     # Single test method
./mvnw test -Dtest=*IntegrationTest         # Pattern matching
```

### Run Application
```bash
cd backend/cvect
./mvnw spring-boot:run
```

---

## Code Style Guidelines

### Package Structure
```
com.walden.cvect
├── CvectApplication          # Main entry point
├── exception                 # Custom exceptions
├── infra                     # Infrastructure layer (parsers, processors)
│   ├── parser/
│   └── process/
├── model                     # Domain models
│   ├── fact/
│   │   ├── extract/          # Fact extractors
│   │   └── rules/            # Business rules
│   └── [other models]
├── repository                # Data access
└── service                   # Business logic
```

### Naming Conventions
- **Classes**: PascalCase (`ResumeChunk`, `FactDecision`)
- **Interfaces**: PascalCase, describe capability (`ChunkerService`, `FactChunkSelector`)
- **Methods**: camelCase (`getContent()`, `apply(ChunkFactContext ctx)`)
- **Constants**: UPPER_SNAKE_CASE (`MAX_CHARS`, `MIN_CHUNK_LENGTH`)
- **Variables**: camelCase (`buffer`, `currentType`)
- **Private fields**: camelCase, final where immutable (`content`, `type`)

### Types and Immutability
- **Prefer immutable value objects**: Use `final` fields, no setters
- **Factory methods over constructors**: `FactDecision.accept(reason)` vs `new FactDecision(...)`
- **Final classes**: Mark classes as `final` when not designed for extension
- **Enumerations**: Use `enum` for fixed sets (`ChunkType`, `FactDecision.Type`)

### Annotations
- `@Component` / `@Service` / `@Repository` for Spring beans
- `@FunctionalInterface` for single-method interfaces
- `@Override` on overridden methods
- `@Autowired` for dependency injection (constructor injection preferred)

### Import Organization
1. `java.*` imports
2. `javax.*` / `jakarta.*` imports
3. Third-party libraries (`org.springframework.*`, `org.apache.tika.*`)
4. `com.walden.cvect.*` (internal)

### Exception Handling
- Custom exceptions extend `RuntimeException`
- Always provide `message` and optional `cause` constructor
- Use descriptive error messages with context
- Preserve original exceptions: `throw new ResumeParseException("Failed to parse", cause)`
- Catch specific exceptions, translate to domain exceptions

### Comments
- Use Chinese comments for business logic explanations
- Javadoc for public APIs
- Inline comments for "why", not "what"
- Example: `// Header 永远不吞非 HEADER` (explain intent)

### Testing Guidelines
- Use JUnit 5 (`@Test`, `@BeforeEach`, etc.)
- `@SpringBootTest` for integration tests
- `@Tag("integration")`, `@Tag("pipeline")` for test categorization
- `@DisplayName("描述性测试名称")` for test documentation
- Static import assertions: `import static org.junit.jupiter.api.Assertions.*`
- Structure: `// Given: setup`, `// When: action`, `// Then: verify`

### Code Patterns
- **Strategy pattern**: Interfaces with multiple implementations (`ChunkerService` → `DefaultChunkerService`)
- **Rule engine**: `ChunkFactRule` functional interface with `RuleBasedFactChunkSelector`
- **Factory pattern**: Static factory methods for complex object creation
- **Null safety**: Guard clauses (`if (input == null || input.isBlank()) return`)
- **Early returns**: Reduce nesting with early guard clauses

### Service Layer
- Define interfaces for services (`ChunkerService`, `ResumeParser`)
- Implementations annotated with `@Service` or `@Component`
- Business logic in service layer, infrastructure in `infra` package
- Use `@Autowired` for constructor injection

### Domain Models
- Value objects: `final` fields, no mutable state
- Getters only, no setters for immutable objects
- `equals()` and `hashCode()` when objects are compared
- `toString()` for debugging

### Constants and Configuration
- Define constants at class level, `private static final`
- Use meaningful names: `MAX_CHARS` not `M`, `MIN_CHUNK_LENGTH` not `N`
- Magic numbers should be named constants

### Git Workflow Notes
- Backend is the only active component (frontend/, ml/, scripts/ are empty placeholders)
- Spring Boot configured with `DataSourceAutoConfiguration` excluded (database optional)
- Use `./mvnw` wrapper for consistent Maven execution

---

## Agent-Specific Notes

When working in this codebase:
1. **All Java work** is in `backend/cvect/src/main/java/`
2. **Tests** are in `backend/cvect/src/test/java/`
3. **Resources** in `backend/cvect/src/main/resources/`
4. Follow existing patterns before introducing new ones
5. Check for existing implementations in same package before adding new classes
6. Run `./mvnw test` after changes to verify
7. Run single test with `-Dtest=ClassName` during development
