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

## Environments

- Local development: Docker Compose for PostgreSQL and Redis; frontend and backend run on the host
- Production topology is not implemented in the foundation phase
