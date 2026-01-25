# CVect Java Package Structure

**Generated:** 2026-01-25
**Commit:** 69da5d4
**Branch:** main

## OVERVIEW
Root package `com.walden.cvect` - layered Spring Boot architecture for resume parsing.

## PACKAGE HIERARCHY
```
com.walden.cvect/
├── model/           # Domain models + fact extraction (31 files)
│   ├── entity/     # JPA entities (Contact, Education, etc.)
│   ├── fact/       # Rule-based extraction (see fact/AGENTS.md)
│   └── entity/vector/ # Vector entities (pgvector)
├── infra/          # External integrations (8 files)
│   ├── parser/     # PDF parsing (Apache Tika)
│   ├── vector/     # pgvector storage
│   ├── embedding/  # ML embedding client
│   └── process/    # Text normalization
├── service/        # Business logic interfaces (4 files)
├── web/controller/ # REST API endpoints (2 files)
├── repository/     # Spring Data JPA (6 files)
├── dto/            # Data transfer objects
└── exception/      # Custom exceptions
```

## LAYER RESPONSIBILITIES
| Layer | Purpose | Key Patterns |
|-------|---------|--------------|
| **Model** | Domain entities + business rules | JPA entities, functional interfaces (`ChunkFactRule`), factory methods |
| **Infrastructure** | External integrations | `@Configuration` classes, HTTP clients (`WebFlux`), parser wrappers |
| **Service** | Business logic | Interface contracts, `@Transactional`, `*ServiceImpl` implementations |
| **Web** | API layer | `@RestController`, `@Valid` validation, DTO conversion |
| **Repository** | Data access | Spring Data JPA, custom queries, pagination |

## KEY CONVENTIONS
### Model Layer
- **Entities**: Protected no-arg constructor, `@PrePersist` for timestamps
- **Fact extraction**: Rules return `FactDecision`, Chinese comments for logic
- **Vector separation**: pgvector entities in separate package

### Infrastructure Layer  
- **External services**: Abstract behind interfaces (e.g., `ResumeParser`)
- **Configuration**: Externalize to `application.yml`
- **HTTP clients**: Use `WebClient` for async calls

### Service Layer
- **Interface segregation**: One interface per domain concern
- **Implementation naming**: `InterfaceName + "Impl"` suffix
- **Transaction boundaries**: `@Transactional` on service methods

## ANTI-PATTERNS
- **NEVER** put business logic in controllers
- **NEVER** expose JPA entities in API responses (use DTOs)
- **NEVER** use `@Autowired` field injection in services
- **ALWAYS** validate input at controller boundary
- **ALWAYS** provide Chinese comments for complex business rules

## INTEGRATION FLOWS
1. **PDF → Text**: `TikaResumeParser` extracts text from PDFs
2. **Text → Facts**: `FactExtractorDispatcher` applies rule-based extraction
3. **Text → Vectors**: `EmbeddingService` calls ML service → `VectorStoreService`
4. **API → Service**: Controllers delegate to services, return DTOs

## NOTES
- **Dependency direction**: web → service → model (infrastructure can be used by any layer)
- **Testing**: Mirror package structure in `src/test/java/`
- **Chinese comments**: Required for business logic, optional for boilerplate