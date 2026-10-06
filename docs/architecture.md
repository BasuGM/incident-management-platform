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

## Multi-tenancy (Phase 3)

- Organizations with unique slugs and membership roles (`OWNER`, `ADMIN`, `MEMBER`, `VIEWER`)
- Teams scoped to organizations; team members must be organization members
- `OrganizationAuthorizationService` enforces tenant isolation on every organization/team operation
- Frontend organization selector stores only the selected organization ID client-side; authorization remains server-side

## Environments

- Local development: Docker Compose for PostgreSQL and Redis; frontend and backend run on the host

### Organization UI routes (Phase 3–4)

- `/organizations` — list/create organizations
- `/organizations/[organizationId]` — organization overview (members, teams, services preview)
- `/organizations/[organizationId]/teams` — team list
- `/organizations/[organizationId]/teams/[teamId]` — team detail
- `/organizations/[organizationId]/services` — service catalog list and create (OWNER/ADMIN)
- `/organizations/[organizationId]/services/[serviceId]` — service detail
- `/organizations/[organizationId]/incidents` — incident list (paginated)
- `/organizations/[organizationId]/incidents/new` — create incident
- `/organizations/[organizationId]/incidents/[incidentId]` — incident detail

- Production topology is not implemented in the foundation phase
