# Phase 5 — Incident management (design)

**Status:** design only — not implemented  
**Last updated:** 2026-10-06

This document defines the **Incident Core** scope for Phase 5. It does not implement code, migrations, or APIs.

---

## 1. Executive summary

Incidents are **organization-scoped** records with an **optional** link to a catalog **Service**, a human-readable **per-organization incident number**, a small **status lifecycle**, **severity**, and **ownership** via reporter and optional incident commander. REST APIs follow the existing `/api/v1/organizations/{organizationId}/…` pattern and reuse `OrganizationAuthorizationService` for tenant isolation (cross-org access returns **403**, not 404).

Phase 5 delivers CRUD-ish incident operations, list filtering, **cursor-free offset pagination**, status/severity workflows via a single **PATCH** resource, and frontend list/create/detail flows. **No** comments, timeline UI, notifications, on-call, postmortems, or event store in Phase 5 — but the model leaves clear extension points.

---

## 2. Goals

- Model incidents under `Organization → Incident`, with optional `Service`, without changing the Service catalog.
- Enforce **strict organization isolation** consistent with teams and services.
- Use existing **organization roles** (OWNER, ADMIN, MEMBER, VIEWER) for authorization.
- Provide a **developer-friendly** identifier (org-scoped incident number) plus UUID for APIs.
- Support **concurrent incident creation** without duplicate numbers.
- Ship list/detail/create/update flows with **pagination and filters** suitable for growth.
- Preserve audit-friendly data (no hard delete in Phase 5).

## 3. Explicit non-goals (Phase 5)

- Incident comments, @mentions, or rich collaboration (Phase 6).
- Full incident timeline / event stream UI (Phase 6+).
- Notifications, webhooks, Redis, WebSockets (later phases).
- On-call schedules and escalation policies (Phase 8–9).
- Postmortem entities and workflows (Phase 7).
- Deployments / change correlation (Phase 11).
- Dashboards and metrics aggregates (Phase 12).
- Global audit log product (Phase 13).
- Email/Slack/PagerDuty integrations.
- Changing global `User.role` or organization role model.
- Modifying existing Service APIs or schema beyond new FK references **from** incidents **to** services.

---

## 4. Existing architecture (inspected)

### Backend

| Area | Location / pattern |
|------|---------------------|
| Organization tenancy | `organization/` — `Organization`, `OrganizationMember`, `OrganizationRole` |
| Authorization | `OrganizationAuthorizationService` — `requireMembership`, `requireRole`, `requireTeamInOrganization`, `requireServiceInOrganization`; failures throw `ForbiddenException` → **403** |
| Teams | `team/` — nested under org; team members must be org members |
| Services | `service/` — `Service` entity, `ServiceRepository.findByIdAndOrganizationId`, REST under `/api/v1/organizations/{organizationId}/services` |
| Users | `user/` — UUID users, global `UserRole` |
| Security | JWT + `UserPrincipal` on controllers |
| Errors | `ApiErrorResponse` via `GlobalExceptionHandler`; domain errors use `ApiClientException` subclasses (e.g. `ConflictException` with `error` code string) |
| Migrations | Flyway `V1`–`V5` in `backend/src/main/resources/db/migration/`; `app` schema; UUID PKs; `TIMESTAMPTZ` |
| Integration tests | `@SpringBootTest` + `MockMvc`, `IntegrationTestBase`, `DatabaseCleaner.cleanAll()` (order: services → teams → orgs → users) |
| DTOs | Java records, `from(Entity)` mappers on response records |
| Controllers | `@RestController`, path variables as `UUID`, `@AuthenticationPrincipal UserPrincipal` |

### Frontend

| Area | Pattern |
|------|---------|
| API client | `src/lib/api/*.ts` via `getAuthenticatedClient()` |
| Types | `src/types/organization.ts` (extend or add `incident.ts`) |
| Org RBAC | `canManageOrganization` (OWNER/ADMIN) — incidents need additional helpers for MEMBER vs VIEWER |
| Org navigation | `OrganizationNav` — Overview, Teams, Services, Members, Settings |
| Data fetching | TanStack Query in page components |
| E2E | Playwright under `frontend/tests/`, API setup via `request` + login helpers |

### Documentation

- `docs/development-plan.md` — Phase 5 marked **next**
- `docs/architecture.md` — multi-tenancy and org UI routes (Phase 3–4)
- `docs/database.md` — V4/V5 table conventions
- `docs/api.md` — REST and error envelope

---

## 5. Incident domain model

### Core entity: `Incident`

| Field | Type | Required | Phase 5 | Rationale |
|-------|------|----------|---------|-----------|
| `id` | UUID | yes | yes | Internal PK; stable forever; used in URLs and FKs (postmortems, future events). |
| `organization` | FK → `organizations` | yes | yes | Tenant root; every query scoped by `organization_id`. |
| `service` | FK → `services` | no | yes | Optional impact surface when known; org-level or unknown-scope incidents may omit. See §9. |
| `incidentNumber` | integer | yes | yes | Human-friendly, org-scoped sequence; see §12. |
| `title` | string | yes | yes | Short summary for lists and notifications (future). |
| `description` | text | no | yes | Initial context; editable while open. |
| `severity` | enum | yes | yes | Operational urgency; see §7. |
| `status` | enum | yes | yes | Lifecycle; see §6. |
| `reporter` | FK → `users` | yes | yes | Creator; immutable in Phase 5. |
| `commander` | FK → `users` | no | yes | Optional incident commander; set at create or later. |
| `createdAt` | instant | yes | yes | Standard audit anchor. |
| `updatedAt` | instant | yes | yes | Any field change. |
| `acknowledgedAt` | instant | no | yes | Set when entering `ACKNOWLEDGED`; supports future timeline without event table. |
| `resolvedAt` | instant | no | yes | Set when entering `RESOLVED`. |
| `cancelledAt` | instant | no | yes | Set when entering `CANCELLED` (see §10). |

### Deferred fields (not on `Incident` in Phase 5)

| Concept | Defer to | Notes |
|---------|----------|-------|
| `priority` | Later / maybe never | Severity is sufficient for v1; avoid duplicate urgency dimensions. |
| Separate `assignee` | Phase 6+ | Commander covers IC ownership; on-call can map to commander assignment. |
| `teamId` on incident | Defer | Service already has optional owning team; derive display from service. |
| `source` / `externalId` | Integrations phase | For webhook ingest. |
| `labels` / `tags` | Later | JSON or join table when needed. |
| Soft-delete flag | Use `CANCELLED` status instead | Simpler queries for “active” incidents. |

---

## 6. Lifecycle / state machine

### Status enum (Phase 5)

| Status | Meaning |
|--------|---------|
| `OPEN` | Newly declared; not yet acknowledged. **Initial status.** |
| `ACKNOWLEDGED` | Someone is actively owning response. |
| `RESOLVED` | Impact mitigated; incident complete from ops perspective. |
| `CANCELLED` | Created in error or duplicate; excluded from “active” metrics. |

**Deferred:** `INVESTIGATING`, `MITIGATED`, `CLOSED` — add in Phase 6+ if finer granularity or postmortem-gated closure is needed. `RESOLVED` is terminal for Phase 5 user workflows.

### Allowed transitions

```text
OPEN          → ACKNOWLEDGED | RESOLVED | CANCELLED
ACKNOWLEDGED  → RESOLVED     | CANCELLED
RESOLVED      → (none in Phase 5)
CANCELLED     → (none in Phase 5)
```

- **Skip acknowledge:** `OPEN → RESOLVED` allowed (small teams).
- **Reopen:** not in Phase 5 (avoids history complexity); add in Phase 6 with event log.

### Transition side effects (on `Incident` row)

| Transition | Timestamps |
|------------|------------|
| → `ACKNOWLEDGED` | set `acknowledgedAt` if null |
| → `RESOLVED` | set `resolvedAt` |
| → `CANCELLED` | set `cancelledAt` |

Do not clear timestamps when moving forward.

### Who may transition

| Action | OWNER | ADMIN | MEMBER | VIEWER |
|--------|-------|-------|--------|--------|
| OPEN → ACKNOWLEDGED | yes | yes | yes | no |
| OPEN → RESOLVED | yes | yes | yes | no |
| ACKNOWLEDGED → RESOLVED | yes | yes | yes | no |
| → CANCELLED | yes | yes | no | no |

(Commander-only transitions can be added later; Phase 5 keeps org-role rules only.)

### Future timeline compatibility

- Phase 5 stores **milestone timestamps on the incident row** for acknowledge/resolve/cancel.
- Phase 6 should introduce an **`incident_events`** (or `incident_status_history`) table and emit rows on each transition; Phase 5 service layer should centralize `applyStatusTransition(incident, newStatus, actor)` so Phase 6 can hook one method.
- Do **not** build an event store in Phase 5.

---

## 7. Severity model

### Enum (Phase 5)

| Value | Semantics |
|-------|-----------|
| `SEV1` | Critical customer/production impact; immediate response. |
| `SEV2` | Major degradation or imminent critical risk. |
| `SEV3` | Moderate impact or workaround available. |
| `SEV4` | Minor / internal / low urgency. |

### Rules

- Required on create; default none (client must choose).
- **Mutable** while not `RESOLVED` or `CANCELLED` by OWNER, ADMIN, MEMBER.
- VIEWER: read-only.
- Future notifications subscribe to severity changes (§21).

---

## 8. Ownership model

| Role | Phase 5 usage |
|------|----------------|
| **Reporter** | Always the authenticated creator (`reporter_id`); not reassignable in Phase 5. |
| **Commander** | Optional `commander_id`; must be an **organization member** if set. Primary human owner during response. |
| **Service → team** | Informational via `ServiceResponse.teamId` / `teamName`; not duplicated on incident. |

**On-call (Phase 8):** future schedulers can **propose** or **auto-set** `commander_id` without schema changes.

---

## 9. Service association

**Decision: service is optional (`service_id` nullable).**

There is no product requirement that every incident must belong to a catalog service. Incidents may be declared before a service is identified, for organization-wide impact, or for workflows that do not map cleanly to a single catalog entry.

| Option | Verdict |
|--------|---------|
| A. Required | Rejected for Phase 5 — forces placeholder services and blocks legitimate org-level incidents. |
| B. Optional | **Adopted** — `service_id` may be `NULL`; when set, the service must belong to the incident’s organization. |

**FK behavior:** `service_id` → `app.services(id)` **`ON DELETE RESTRICT`**. A service referenced by any incident cannot be deleted until incidents are reassigned or cancelled (or `service_id` cleared via PATCH if product allows). Service delete may return `409 SERVICE_HAS_INCIDENTS` when restricted rows exist.

**Validation:**

- On create/update, if `serviceId` is **present** (non-null in JSON), call `requireServiceInOrganization(organizationId, serviceId, userId)` — same as today for services.
- If `serviceId` is **omitted** or explicitly **`null`**, leave `service_id` null; **organization isolation is enforced via `organization_id`**, not via the service FK.
- On create, `organization_id` comes from the URL path (`organizationId`); it must not be inferred only from the service (a cross-org service id must still fail via `requireServiceInOrganization`).

---

## 10. Delete / cancellation

**Recommendation: no hard delete in Phase 5.**

| Approach | Verdict |
|----------|---------|
| Hard delete | Rejected — breaks postmortem FKs and metrics. |
| Soft delete column | Possible later; redundant with status for v1. |
| `CANCELLED` status | **Adopted** — OWNER/ADMIN only; excluded from default “active” list filter. |

No `DELETE /incidents/{id}` in Phase 5 API.

---

## 11. Organization isolation & authorization

### Rules (mirror services)

1. Every incident row has `organization_id` from the request path. When `service_id` is set, the service’s organization must match that `organization_id` (enforced via `requireServiceInOrganization` on write). When `service_id` is null, `organization_id` alone defines tenancy.
2. **List / get:** `requireMembership(organizationId, userId)`.
3. **Get by id:** `requireIncidentInOrganization(organizationId, incidentId, userId)` — load by `(id, organizationId)` or verify join; else **403**.
4. **Cross-org URL** `GET /organizations/A/incidents/{idFromB}` → **403** (`ForbiddenException`), same as services — **no leak** that incident exists in B.

### `OrganizationAuthorizationService` extension (implementation note)

Add:

```java
Incident requireIncidentInOrganization(UUID organizationId, UUID incidentId, UUID userId);
```

Implementation pattern: same as `requireServiceInOrganization`.

### Commander / reporter validation

- `reporter_id`: always current user on create.
- `commander_id`: if present, user must have `organization_members` row for `organizationId`.

---

## 12. RBAC summary

| Operation | OWNER | ADMIN | MEMBER | VIEWER |
|-----------|-------|-------|--------|--------|
| List / get incidents | yes | yes | yes | yes |
| Create incident | yes | yes | yes | no |
| PATCH title, description, severity, commander | yes | yes | yes | no |
| PATCH status (non-cancel) | yes | yes | yes | no |
| Cancel (`CANCELLED`) | yes | yes | no | no |

Global `User.role` does not gate incident APIs in Phase 5 (only org membership + org role).

---

## 13. Database design (proposed — not migrated)

### Table: `app.incidents`

```sql
CREATE TABLE app.incidents (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES app.organizations (id) ON DELETE CASCADE,
    service_id UUID REFERENCES app.services (id) ON DELETE RESTRICT,
    incident_number INTEGER NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    severity VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reporter_id UUID NOT NULL REFERENCES app.users (id) ON DELETE RESTRICT,
    commander_id UUID REFERENCES app.users (id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT uq_incidents_organization_number UNIQUE (organization_id, incident_number),
    CONSTRAINT chk_incidents_severity CHECK (severity IN ('SEV1', 'SEV2', 'SEV3', 'SEV4')),
    CONSTRAINT chk_incidents_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'CANCELLED'))
);

CREATE INDEX idx_incidents_organization_id ON app.incidents (organization_id);
CREATE INDEX idx_incidents_organization_status ON app.incidents (organization_id, status);
CREATE INDEX idx_incidents_organization_created_at ON app.incidents (organization_id, created_at DESC);
CREATE INDEX idx_incidents_service_id ON app.incidents (service_id);
CREATE INDEX idx_incidents_commander_id ON app.incidents (commander_id);
```

**Active incidents** (application query): `status IN ('OPEN', 'ACKNOWLEDGED')`.

### Table: `app.organization_incident_counters` (numbering)

```sql
CREATE TABLE app.organization_incident_counters (
    organization_id UUID PRIMARY KEY REFERENCES app.organizations (id) ON DELETE CASCADE,
    next_number INTEGER NOT NULL
);
```

---

## 14. Incident numbering & concurrency

**Decision: organization-scoped sequential integers** (not global, not timestamp-based).

- Display: **`INC-{incidentNumber}`** in UI (org context is implicit from URL/selector).
- API responses include `incidentNumber` (integer) and optional `displayId` string `"INC-42"` for convenience.

### Concurrency-safe allocation

Within the same DB transaction as insert:

1. `INSERT INTO organization_incident_counters (organization_id, next_number) VALUES (:orgId, 2) ON CONFLICT (organization_id) DO UPDATE SET next_number = organization_incident_counters.next_number + 1 RETURNING next_number - 1 AS assigned_number`  
   — or equivalent `SELECT … FOR UPDATE` on counter row.

2. Insert `incidents` with `incident_number = assigned_number`.

Unique constraint `(organization_id, incident_number)` is the backstop.

**URLs:** use **UUID** in paths (`/incidents/{incidentId}`), not `INC-1024`, to stay consistent with services/teams and avoid routing ambiguity.

---

## 15. API design

### Base path

```text
/api/v1/organizations/{organizationId}/incidents
```

### Endpoints (Phase 5)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/incidents` | Paginated list with filters |
| `POST` | `/incidents` | Create (201) |
| `GET` | `/incidents/{incidentId}` | Detail |
| `PATCH` | `/incidents/{incidentId}` | Partial update (fields + status) |

No `DELETE`. No separate `/acknowledge` actions in Phase 5 — **PATCH `status`** keeps one code path; transition rules live in `IncidentService`.

### `POST` — `CreateIncidentRequest`

```json
{
  "serviceId": "optional uuid",
  "title": "string",
  "description": "optional string",
  "severity": "SEV2",
  "commanderId": "optional uuid"
}
```

Validation: title length, severity enum, service in org **when `serviceId` is provided**, commander in org if set.

### `PATCH` — `UpdateIncidentRequest`

All fields optional; only sent fields apply (same PATCH semantics as services):

```json
{
  "title": "string",
  "description": "string",
  "severity": "SEV1",
  "serviceId": "uuid | null",
  "commanderId": "uuid | null",
  "status": "ACKNOWLEDGED"
}
```

- `serviceId: null` clears the service link (only if allowed when not resolved/cancelled); non-null values must pass `requireServiceInOrganization`.
- `commanderId: null` clears commander (only if allowed when not resolved/cancelled).
- Invalid status transition → `409` / `INVALID_INCIDENT_STATUS_TRANSITION`.

### `IncidentResponse`

```json
{
  "id": "uuid",
  "organizationId": "uuid",
  "incidentNumber": 42,
  "displayId": "INC-42",
  "serviceId": "uuid | null",
  "serviceName": "Payments | null",
  "title": "...",
  "description": "...",
  "severity": "SEV2",
  "status": "OPEN",
  "reporterId": "uuid",
  "reporterEmail": "user@example.com",
  "reporterFirstName": "Ada",
  "reporterLastName": "Lovelace",
  "commanderId": null,
  "commanderEmail": null,
  "createdAt": "...",
  "updatedAt": "...",
  "acknowledgedAt": null,
  "resolvedAt": null,
  "cancelledAt": null
}
```

Denormalized names/emails match `ServiceResponse` / member list patterns for UI convenience.

### List — `GET /incidents`

Query parameters:

| Param | Type | Notes |
|-------|------|-------|
| `status` | enum | Exact match; repeatable or comma-separated (pick one style in impl; recommend single value first) |
| `severity` | enum | Optional |
| `serviceId` | uuid | Optional |
| `commanderId` | uuid | Optional |
| `active` | boolean | If `true`, `status IN (OPEN, ACKNOWLEDGED)`; if `false`, resolved/cancelled only |
| `page` | int | 0-based, default `0` |
| `size` | int | default `20`, max `100` |
| `sort` | string | default `createdAt,desc` (see below) |

**Pagination:** **Required in Phase 5** — return a page wrapper to avoid breaking clients later:

```json
{
  "content": [ /* IncidentResponse */ ],
  "page": 0,
  "size": 20,
  "totalElements": 153,
  "totalPages": 8
}
```

**Default sort:** `createdAt` descending (newest first). Optional Phase 5 enhancement: sort active statuses before resolved when `active` is not set — can be `ORDER BY CASE status … END, created_at DESC` or document as follow-up.

### HTTP status codes

| Code | When |
|------|------|
| 201 | Created |
| 200 | OK |
| 400 | Validation |
| 401 | Unauthenticated |
| 403 | Not org member or role forbidden |
| 409 | Conflict (invalid transition, etc.) |

---

## 16. Filtering & sorting (summary)

**Minimum useful filters:** `active`, `status`, `severity`, `serviceId`.  
**Defer:** created date range until needed.

---

## 17. Frontend routes & IA

### Routes

```text
/organizations/[organizationId]/incidents
/organizations/[organizationId]/incidents/[incidentId]
```

`incidentId` = UUID.

### Navigation

- Add **Incidents** to `OrganizationNav` (between Services and Members or after Services).
- Add overview hub card on organization home (like Teams/Services).

### Incident list page

- Table/cards: `displayId`, severity badge, status, title, service name, commander, created time.
- Filters: active toggle, status, severity, service (select from org services).
- Primary action: **Create incident** (hidden for VIEWER).
- Pagination controls bound to API `page`/`size`.

### Create incident

- Fields: service (optional), title, description, severity, optional commander (member picker or user ID field matching members UX — prefer select populated from members API, plus explicit “No service” / empty option).
- On success: navigate to detail.

### Incident detail

- Header: `displayId`, title, severity, status.
- Metadata: service (link when set; otherwise “No service” / org-level label), reporter, commander, timestamps.
- Description (editable for MEMBER+).
- Actions: status buttons or select (Acknowledge, Resolve, Cancel per RBAC).
- **Future extension region:** below main content, reserved layout for tabs:

```text
[ Overview ] [ Timeline ] [ Comments ] …
```

Phase 5 implements **Overview** only; other tabs hidden or disabled with “Coming later”.

### RBAC helpers (frontend)

```ts
canCreateIncident(role) => role !== "VIEWER"
canUpdateIncident(role) => role !== "VIEWER"
canCancelIncident(role) => role === "OWNER" || role === "ADMIN"
```

---

## 18. TanStack Query keys & invalidation

| Key | Usage |
|-----|--------|
| `["incidents", organizationId, { filters, page, size }]` | List |
| `["incident", organizationId, incidentId]` | Detail |

**Invalidate after:**

- Create: list (+ optionally prefetch detail).
- PATCH: detail + list for that org.
- Status change: same as PATCH.

Use `queryClient.setQueryData` for PATCH response on detail key when possible.

---

## 19. Audit / event history strategy

| Option | Phase 5 |
|--------|---------|
| A. No history | **Yes** — only row timestamps |
| B. Status history table | No |
| C. Generic `incident_events` | No |
| D. Platform audit infra | No |

**Migration path (Phase 6):** introduce `app.incident_events` with `incident_id`, `type`, `payload JSONB`, `actor_id`, `created_at`. Refactor `applyStatusTransition` to append events. Backfill optional from `acknowledged_at` / `resolved_at` on existing rows.

---

## 20. Future-phase compatibility

### On-call (Phase 8)

- No provider-specific columns.
- `commander_id` is the integration point for “who is IC now.”

### Notifications (Phase 10)

Future subscribers care about domain events (not implemented in Phase 5):

- `incident.created`
- `incident.severity_changed`
- `incident.status_changed` (acknowledged, resolved, cancelled)
- `incident.commander_changed`

**Origin:** Phase 6+ event emission from `IncidentService` after commit (or outbox table later).

### Postmortems (Phase 7)

- Postmortem will FK `incident_id` → `incidents.id` (UUID).
- `incidentNumber` + `organizationId` remain stable display keys.

---

## 21. Error model

Use `ConflictException` / `ApiClientException` with stable `error` codes:

| Code | HTTP | When |
|------|------|------|
| `VALIDATION_ERROR` | 400 | Bean validation |
| `FORBIDDEN` | 403 | Not a member / role |
| `INCIDENT_NOT_FOUND` | 403 | Prefer same as services: use **403** via `ForbiddenException` for wrong org; reserve 404 only if product later distinguishes |
| `SERVICE_NOT_IN_ORGANIZATION` | 403 | Invalid service (via existing helper) |
| `COMMANDER_NOT_IN_ORGANIZATION` | 400 | Commander not a member |
| `INVALID_INCIDENT_STATUS_TRANSITION` | 409 | Illegal transition |
| `INVALID_INCIDENT_SEVERITY` | 400 | Bad enum |
| `INCIDENT_NOT_EDITABLE` | 409 | PATCH on resolved/cancelled |

**Note:** Today cross-org service access does not use `NOT_FOUND` — incidents should **match** (403).

`DUPLICATE_INCIDENT_NUMBER` should not surface if allocation is transactional; keep as internal safeguard / 409 if unique violation.

---

## 22. Backend package structure (proposed)

```text
com.example.incidentmanagement.incident/
  Incident.java
  IncidentStatus.java
  IncidentSeverity.java
  IncidentRepository.java
  OrganizationIncidentCounter.java
  OrganizationIncidentCounterRepository.java
  IncidentService.java
  IncidentController.java
  dto/
    CreateIncidentRequest.java
    UpdateIncidentRequest.java
    IncidentResponse.java
    IncidentPageResponse.java
```

Optional: `IncidentStatusTransition` package-private helper or methods on `IncidentService`.

Extend `OrganizationAuthorizationService` in `organization/` (not under `incident/`).

---

## 23. Test strategy (for implementation)

### Backend unit (`IncidentServiceTest`)

- Create assigns number, reporter, OPEN status.
- Service must belong to org when `serviceId` is supplied; create without service allowed.
- Clearing `serviceId` via PATCH (null) when allowed by edit rules.
- Commander must be org member.
- Severity validation.
- Status transitions valid/invalid.
- Resolved/cancelled immutability rules.
- Role checks (viewer cannot create).

### Backend integration (`IncidentIntegrationTest`)

- Full CRUD flow (no delete).
- Cross-org get → 403.
- Service from other org on create → 403.
- RBAC matrix spot checks.
- Pagination and filters (`active`, `serviceId`).
- Concurrent create: two threads, distinct `incidentNumber` (integration or Testcontainers stress).

### `DatabaseCleaner`

- Delete incidents before services in `cleanAll()`.

### Frontend E2E (`incidents.spec.ts`)

- Owner creates incident, appears in list with INC-n.
- Detail shows service, severity, status.
- Member updates status; viewer read-only.
- Cancel: admin yes, member no.
- Org isolation smoke.
- Filters + pagination smoke.
- Regression: existing 15 tests still pass.

---

## 24. Open decisions

| Decision | Recommendation |
|----------|----------------|
| Service mandatory? | **No** — optional; validate org membership of service when supplied |
| Incident number scope? | **Per organization** |
| Hard delete? | **No** — use `CANCELLED` |
| Status set? | **OPEN, ACKNOWLEDGED, RESOLVED, CANCELLED** |
| Who transitions? | **MEMBER+**; cancel **OWNER/ADMIN** |
| Status history in Phase 5? | **No** — milestone timestamps only |
| Pagination in v1? | **Yes** — page wrapper |
| Commander nullable? | **Yes** |
| Severity mutable? | **Yes** until terminal status |
| URL id? | **UUID**; display INC-n in UI |
| Reopen resolved? | **Defer** to Phase 6 |
| Default list sort? | **`createdAt` desc**; optional active-first later |
| 403 vs 404 cross-org? | **403** (match services) |

---

## 25. Phase 5 implementation plan

| Step | Work | Depends on |
|------|------|------------|
| 1 | Flyway `V6__incidents.sql` + entities/repos/counter | — |
| 2 | `requireIncidentInOrganization` + `IncidentService` (create, get, list, patch, transitions) | 1 |
| 3 | `IncidentController` + DTOs + validation | 2 |
| 4 | Unit + integration tests; update `DatabaseCleaner` | 3 |
| 5 | `src/lib/api/incidents.ts` + types + query keys | 3 |
| 6 | List / create / detail pages + `OrganizationNav` | 5 |
| 7 | Status/severity/commander UI + RBAC helpers | 6 |
| 8 | Playwright `incidents.spec.ts` | 7 |
| 9 | Manual browser verification | 8 |
| 10 | Update `docs/api.md`, `docs/database.md`, `docs/architecture.md`; mark Phase 5 complete in `development-plan.md` | 9 |

---

## 26. Definition of done (Phase 5)

- [ ] Migrations and backend tests green (`./gradlew test`).
- [ ] Incident APIs documented in `docs/api.md`.
- [ ] Frontend list/create/detail/edit/status flows under org routes.
- [ ] RBAC and org isolation verified in integration + E2E tests.
- [ ] Pagination and core filters shipped.
- [ ] No hard delete; cancel path works for OWNER/ADMIN.
- [ ] Playwright suite passes (existing 15 + new incident tests).
- [ ] `npm run lint`, `typecheck`, `build` pass.

---

## 27. Concerns for Phase 1–4 before implementation

1. **`DatabaseCleaner` order** — incidents must be deleted before services when tests land.
2. **Service delete** — today services can be deleted; with incidents FK `RESTRICT`, implement guard or document “no delete if incidents exist” (409).
3. **403 convention** — incident APIs must not introduce 404 for cross-tenant IDs (stay consistent).
4. **Frontend RBAC** — only `canManageOrganization` exists; incidents need MEMBER-level write helpers (documented above).
5. **`architecture.md` routes** — still list Phase 3–4 paths only; update during Step 10.
6. **No pagination precedent** on list APIs yet — incidents page wrapper becomes the template for future large lists.

---

## 28. References

- Development plan: `docs/development-plan.md` (Phase 5 — **next**, not complete)
- Service catalog patterns: `ServiceController`, `ServiceService`, `ServiceIntegrationTest`
- Tenant auth: `OrganizationAuthorizationService`
