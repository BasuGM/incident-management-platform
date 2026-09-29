# Architectural decisions

## ADR-001: Modular monolith first

**Decision:** Start with a single Spring Boot application with clear package boundaries.

**Reason:** Faster delivery and simpler operations for early phases; avoids premature microservice complexity.

## ADR-002: PostgreSQL + Flyway

**Decision:** Use PostgreSQL with Flyway migrations.

**Reason:** Strong relational modeling for incidents and audit data; repeatable schema evolution across environments.

## ADR-003: Redis in compose, features later

**Decision:** Run Redis in Docker Compose and wire Spring configuration, but defer caching and rate limiting features.

**Reason:** Establishes infrastructure early without building unused code paths.

## ADR-004: Gradle wrapper (manual bootstrap)

**Decision:** Maintain a Gradle wrapper in `backend/` even though start.spring.io Gradle generation was unavailable during setup.

**Reason:** Reproducible builds without a global Gradle install; dependencies align with Spring Boot 4.1.1.

## ADR-005: Frontend server state in TanStack Query

**Decision:** Use TanStack Query for server state; avoid Zustand unless genuine client-only global state is needed.

**Reason:** Clear separation between remote data and UI state.

## ADR-006: No domain implementation in foundation phase

**Decision:** No incident/user/team entities or business APIs in Phase 1.

**Reason:** Establish conventions and tooling before domain complexity.
