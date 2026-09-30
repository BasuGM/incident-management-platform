# Architecture

## System context

```text
Next.js (frontend)
    ↓ HTTP (REST)
Spring Boot API (backend)
    ↓ JDBC
PostgreSQL

Spring Boot API
    ↓ Redis protocol
Redis
```

## Backend style

The backend starts as a **modular monolith**:

- One deployable Spring Boot application
- Clear package boundaries (`config`, `common`, feature packages added later)
- REST API under `/api/v1/*`
- PostgreSQL as the system of record
- Redis reserved for caching, distributed state, rate limiting, and future async/event infrastructure

## Frontend style

- Next.js App Router with TypeScript
- Server state via TanStack Query
- Shared UI primitives via shadcn/ui
- Centralized API client in `src/lib/api/`
- Access tokens held in memory; refresh tokens carried via HttpOnly cookies to the API

## Authentication (Phase 2)

- Spring Security with stateless JWT access tokens
- BCrypt password hashing
- Refresh tokens persisted in PostgreSQL (hashed) with rotation on refresh
- Role-based access control: `ADMIN`, `ENGINEER`, `VIEWER`
- Method-level authorization for user APIs (`@PreAuthorize`)

## Environments

- Local development: Docker Compose for PostgreSQL and Redis; frontend and backend run on the host
- Production topology is not implemented in the foundation phase
