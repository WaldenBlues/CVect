# Backend Architecture Guide

This backend now uses a business-domain package layout for the main web flows.

## Read Order

If you are learning the codebase, read packages in this order:

1. `web/controller/<domain>`: HTTP entrypoints
2. `service/<domain>`: use-case orchestration and domain-facing services
3. `repository` + `model/entity`: persistence model
4. `infra/*`: adapters for parser, embedding, vector store, and text processing
5. `logging`: cross-cutting web logging, audit, and timing concerns

## Package Map

### Web entrypoints

- `web/controller/candidate`: candidate list, detail, status update, candidate SSE stream
- `web/controller/job`: job description CRUD
- `web/controller/resume`: resume parsing and health entrypoint for parsing flow
- `web/controller/search`: search API
- `web/controller/system`: operational health endpoints
- `web/controller/upload`: upload entrypoints and upload batch SSE

### Application and domain services

- `service/candidate`: candidate snapshot assembly for UI-facing events and payloads
- `service/job`: job description write-side orchestration
- `service/matching`: semantic search, score persistence, caching, and weight normalization
- `service/resume`: resume parsing, chunking, fact extraction, and candidate creation
- `service/upload`: upload admission, archive handling, and upload batch operations
- `service/upload/queue`: background upload queue consumption and queue lease helpers
- `service/vector`: vector ingestion submission
- `service/vector/queue`: background vector ingest queue consumption

### Supporting layers

- `repository`: JPA repositories stay flat because they are infrastructure-facing, not use-case-facing
- `infra`: wrappers around external capabilities and low-level adapters
- `logging`: request tracing, controller/service AOP, audit, timed actions, sanitization
  - `logging/web`: request tracing filter and HTTP exception handling
  - `logging/aop`: annotations and aspects
  - `logging/config`: logging properties and bean wiring
  - `logging/mdc`: async MDC propagation
  - `logging/support`: formatter, constants, and value sanitization
- `exception`: API and business exceptions
- `config`: wiring and framework configuration

## Package Rules

- Controllers stay thin. They translate HTTP input/output and delegate immediately.
- Cross-resource write flows live in `service/<domain>` application services.
- Background workers live under `queue` subpackages so they do not mix with synchronous request flows.
- Matching and search logic stay under `service/matching` even when reused by controllers or workers.
- Infrastructure integrations do not own business orchestration.
- Logging is isolated as a cross-cutting module and should not leak business rules.

## Navigation Shortcuts

- Want to understand uploads: start at `web/controller/upload`, then `service/upload`, then `service/upload/queue`
- Want to understand search: start at `web/controller/search`, then `service/matching`
- Want to understand candidate refresh and SSE: start at `web/controller/candidate`, then `service/candidate`
- Want to understand JD lifecycle: start at `web/controller/job`, then `service/job`

## Test Layout

`src/test/java` mirrors the same domain structure for the main backend flows:

- controller tests live under `web/controller/<domain>`
- service tests live under `service/<domain>`
- queue worker tests live under `service/*/queue`
- cross-cutting logging tests live under `logging`

## Current Tradeoff

The main source tree and the main test tree now follow the domain layout consistently.
Some root-level integration tests remain intentionally broad because they validate end-to-end flows across multiple domains.
