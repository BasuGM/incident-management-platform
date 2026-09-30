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

## ADR-007: Browser calls the Spring API directly

**Decision:** The Next.js app uses `NEXT_PUBLIC_API_URL` (default `http://localhost:8080`) and the shared `src/lib/api/client.ts` to call `/api/v1/*` on the backend. The frontend dev server does not expose those routes.

**Reason:** Keeps a single API surface on Spring Boot; Spring Security enables CORS with credentials for local browser origins so TanStack Query can reach the API from `localhost:3000`.

## ADR-008: JWT access tokens + persisted refresh tokens

**Decision:** Issue short-lived HS256 JWT access tokens and long-lived refresh tokens stored in PostgreSQL as SHA-256 hashes. Refresh rotates tokens and supports revocation on logout.

**Reason:** Stateless API authentication with a revocable session mechanism suitable for multiple devices, without server-side HTTP sessions.

## ADR-009: Browser token storage strategy

**Decision:** Store access tokens only in frontend memory (React state module). Store refresh tokens in HttpOnly, `SameSite=Lax` cookies scoped to `/api/v1/auth`, with `credentials: "include"` on API calls. Do not persist refresh tokens in `localStorage`.

**Reason:** Reduces XSS impact on long-lived credentials while keeping the SPA architecture. A full token-in-cookie model would require CSRF defenses and tighter coupling to same-site deployment; that is deferred until production hardening.

## ADR-010: Default registration role

**Decision:** Public registration assigns `ENGINEER`. `ADMIN` cannot be chosen via the public register API.

**Reason:** Safe default for self-service signup while keeping privileged role assignment admin-controlled.

## ADR-011: Password hashing

**Decision:** Use Spring Security `BCryptPasswordEncoder` for password storage.

**Reason:** Industry-standard adaptive hashing without custom crypto.
