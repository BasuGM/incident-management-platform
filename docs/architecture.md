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

## Incidents (Phase 5–6)

- `Incident` — organization-scoped incident record (status, severity, service, commander, milestones)
- `IncidentEvent` — append-only audit events tied to an incident and organization
- `IncidentService` appends events via `IncidentEventRecorder` in the same database transaction as incident create/update (no separate transaction or async emission)
- Timeline read API: `GET .../incidents/{incidentId}/events` loads paginated `IncidentEvent` rows scoped by `organizationId` + `incidentId` after the same incident read authorization as `GET .../incidents/{incidentId}`
- Incident detail UI renders a read-only timeline (human-readable event text + pagination via “Load more”) from that API; TanStack Query keys include `organizationId`, `incidentId`, and the current user id

### Comments (Phase 7 — complete)

- **`IncidentComment`** — persistence/domain layer (`app.incident_comments`, repository, soft delete via `deleted_at` with body cleared on delete); separate from immutable `IncidentEvent`
- **`IncidentCommentService`** — comment CRUD business rules and organization RBAC (read: all members; create: OWNER/ADMIN/MEMBER; author-only edit; author or OWNER/ADMIN soft-delete; terminal incident write lock); no `IncidentEvent` emission
- **REST API** — tenant-scoped `.../incidents/{incidentId}/comments` (GET/POST/PATCH/DELETE), DTOs, thin `IncidentCommentController`; deleted comments remain visible as tombstones in list responses; `touchAuthor()` + repository `@EntityGraph` avoid N+1 and keep author fields serializable with `open-in-view: false`
- **Frontend** — comment API/types, TanStack Query keys scoped by organization, incident, page, size, and user; incident detail Comments section (plain text via `whitespace-pre-wrap`, RBAC-aware composer/edit/delete, tombstones, load-more) placed before Timeline
- **Intentionally excluded from Phase 7:** comment rows in `IncidentEvent`, @mentions, attachments, Markdown/rich text, notifications, WebSockets/SSE/polling
- Design reference: [phase-7-comments-design.md](phase-7-comments-design.md)

### Postmortems (Phase 8)

- **`IncidentPostmortem`** — at most one postmortem per incident (`app.incident_postmortems`); plain-text sections; lifecycle `DRAFT` → `PUBLISHED` → optional `ARCHIVED`; writes only when parent incident is **`RESOLVED`**
- **`IncidentPostmortemService`** — create/read/update/publish/unpublish/archive/unarchive/delete draft; organization RBAC; resolved-incident write eligibility; **no** `IncidentEvent` emission
- **REST API** — singleton `.../incidents/{incidentId}/postmortem` plus paginated `GET .../organizations/{organizationId}/postmortems` (default `status=PUBLISHED`)
- **Frontend** — Postmortem section on incident detail (between metadata and Comments); org library at `/organizations/[organizationId]/postmortems`; TanStack Query keys scoped by organization, incident, user, list filters, and pagination
- **Intentionally excluded from Phase 8:** structured action items, review queues, Markdown, attachments, new audit event types on incidents
- Design reference: [phase-8-postmortems-design.md](phase-8-postmortems-design.md)

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
- `/organizations/[organizationId]/postmortems` — organization postmortems library (Phase 8)

- Production topology is not implemented in the foundation phase
