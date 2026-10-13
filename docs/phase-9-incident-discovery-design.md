# Phase 9 — Incident operational discovery (design)

**Status:** design approved for implementation (Step 1)  
**Last updated:** 2026-10-13  
**Baseline:** `main` @ `1eb88a9` + local Phase 9 planning docs  
**Roadmap:** [phase-9-roadmap.md](phase-9-roadmap.md)

This document defines the **contract** for incident list filtering, search, and URL-synchronized UI before any production code changes. Verified behavior of the current system is distinguished from **proposed** behavior in labeled sections.

---

## 1. Status and scope

### In scope

- Extend `GET /api/v1/organizations/{organizationId}/incidents` with query filters and search.
- Update the organization incidents list page with a filter bar and URL query-parameter sync.
- Backend integration tests, Vitest helpers (if any), and Playwright journeys for discovery flows.

### Out of scope

- Saved views / per-user presets (persistence).
- Notifications, on-call, escalation, postmortem action items.
- New incident statuses or lifecycle transitions.
- Description full-text search, fuzzy search, or query languages.
- Client-controlled `sort` parameter (fixed server ordering only).
- Filter for “no commander” / unassigned incidents (can be a follow-up).
- Elasticsearch, Redis, or other new infrastructure.

---

## 2. Current implementation findings

### 2.1 API (verified)

| Aspect | Current behavior | Source |
|--------|------------------|--------|
| Endpoint | `GET /api/v1/organizations/{organizationId}/incidents` | `IncidentController.java` |
| Query params | `page` (default `0`), `size` (default `20`, clamped `1`–`100`) | `IncidentController.listIncidents` |
| Sort | `createdAt` descending only; **no** tie-break on `id` | `PageRequest.of(..., Sort.by(DESC, "createdAt"))` |
| Response | `IncidentPageResponse` — `content`, `page`, `size`, `totalElements`, `totalPages` | `docs/api.md`, DTO |
| Auth | Any organization member; non-member → `403` | `IncidentService.listIncidents` → `requireOrganizationMember` |
| Filters | **None** | `IncidentRepository.findByOrganizationId(organizationId, pageable)` |

### 2.2 Domain model (verified)

| Field | Notes |
|-------|--------|
| `status` | `OPEN`, `ACKNOWLEDGED`, `RESOLVED`, `CANCELLED` — `IncidentStatus.java` |
| `severity` | `SEV1`–`SEV4` — `IncidentSeverity.java` |
| `service_id` | Optional FK to org service |
| `commander_id` | Optional FK to user; must be org member when set on write |
| `incident_number` | Per-org; `displayId` = `INC-{number}` in `IncidentResponse` |
| Terminal states | `RESOLVED`, `CANCELLED` — not editable (`INCIDENT_NOT_EDITABLE`) |

Lifecycle transitions are enforced in `IncidentService` (`ALLOWED_TRANSITIONS`); **this feature does not change them**.

### 2.3 Database (verified)

`V6__incidents.sql` indexes (all prefixed with `organization_id` where relevant):

- `idx_incidents_organization_created_at` — `(organization_id, created_at DESC)`
- `idx_incidents_organization_status`
- `idx_incidents_organization_severity`
- `idx_incidents_organization_service_id`
- `idx_incidents_organization_commander_id`

No migration is required for the proposed filters; see §9.

### 2.4 Frontend (verified)

| Aspect | Current behavior | Source |
|--------|------------------|--------|
| Route | `/organizations/[organizationId]/incidents` | `incidents/page.tsx` |
| State | Local `useState` for `page` only | Same file |
| URL sync | **None** — no `useSearchParams` anywhere in `frontend/src` today | Repository search |
| API client | `listIncidents(orgId, page, size)` | `lib/api/incidents.ts` |
| Query key | `["incidents", organizationId, page, PAGE_SIZE]` | `incidents/page.tsx` |
| Member/service pickers | Used on create/detail via `listOrganizationMembers`, `listServices` | `incidents/new/page.tsx`, detail page |
| Filter UI precedent | Postmortem library uses `<select>` status filter + local state (no URL sync) | `postmortems/page.tsx` |

### 2.5 Tests (verified)

- `IncidentControllerIntegrationTest` — list with pagination (`page`/`size`), auth, tenant isolation; **no** filter cases.
- `frontend/tests/incidents.spec.ts` — lifecycle, timeline, commander flows; **no** list filter UI tests.

### 2.6 Historical design drift

[phase-5-incident-design.md](phase-5-incident-design.md) §428–441 defines list filters (`active`, `status`, `severity`, `serviceId`, `commanderId`) and optional `sort` — **not implemented**. Phase 9 delivers the minimum filter set plus `search` and `mine`/`commanderId=me` ergonomics.

---

## 3. Goals and non-goals

### Goals

1. Let responders find **active** work quickly.
2. Let commanders see **incidents assigned to them** (`mine`).
3. Triage by **severity**, **status**, and **service**.
4. **Search** by title and incident identifier (`INC-n` / number).
5. Preserve **tenant isolation** and existing RBAC (VIEWER can list/filter read-only).
6. Keep filters **shareable** via URL and stable across refresh.
7. Use **database-side** filtering and pagination (no in-memory full scans).

### Non-goals

Listed in §1; additionally: changing default API behavior to hide resolved incidents without an explicit parameter (see §5.2).

---

## 4. Domain semantics

### 4.1 Active incidents (proposed)

**`active` is a convenience filter over `status`, not a separate persisted field.**

| `active` value | Status predicate |
|----------------|------------------|
| `true` | `status IN ('OPEN', 'ACKNOWLEDGED')` |
| `false` | `status IN ('RESOLVED', 'CANCELLED')` |
| omitted | No status constraint from `active` |

This matches [phase-5-incident-design.md](phase-5-incident-design.md) §438 and the design note that `CANCELLED` is excluded from active metrics.

**Interaction with `status`:**

- If **`status`** is present, it **takes precedence** over `active` for the status dimension: the query uses exact match on `status` only, and **`active` is ignored** (no error).
- Rationale: avoids `409` on innocent UI bugs; explicit `status` is strictly more specific. The frontend **must not** send both when they contradict; when the user picks a specific status, clear or omit `active` in the URL.

**Interaction with `active=false` and terminal statuses:** `active=false` is equivalent to filtering `status IN (RESOLVED, CANCELLED)`; if the user also sets `status=OPEN`, only `status` applies (yielding empty results if no row matches both intents — acceptable).

### 4.2 Commander “mine” (proposed)

**`mine=true`** means: restrict to incidents where `commander_id = authenticated user's id`.

Incidents with **no commander** are excluded when `mine=true`.

This is equivalent to `commanderId=<caller UUID>` but avoids exposing the caller’s UUID in URLs when sharing “my incidents” links.

---

## 5. API query-parameter contract (proposed)

**Endpoint:** `GET /api/v1/organizations/{organizationId}/incidents`  
**Response shape:** unchanged (`IncidentPageResponse`).

### 5.1 Pagination and sorting (existing + proposed fix)

| Parameter | Type | Optional | Default | Validation | Notes |
|-----------|------|----------|---------|------------|-------|
| `page` | integer | yes | `0` | `max(page, 0)` | Unchanged |
| `size` | integer | yes | `20` | clamp `1`–`100` | Unchanged |

**Sorting (not client-configurable in Phase 9):**

- Order: `created_at DESC`, then `id DESC` (tie-break for deterministic pages).
- **Proposed change** from today: add `id DESC` tie-break (today only `createdAt` in `PageRequest`).

No `sort` query parameter in Phase 9 (Phase 5 optional `sort` remains deferred).

### 5.2 Active default decision: **Option B — frontend default**

| Option | Description | Assessment |
|--------|-------------|------------|
| **A — Backend default** | Omitting `active` ⇒ only active incidents | **Rejected** — breaks backward compatibility for any client/tests expecting all incidents (today’s `GET` returns all statuses). |
| **B — Frontend default** | API returns all when `active` omitted; UI defaults to active view with explicit `active=true` in URL | **Chosen** — API stays predictable (`omit` = no filter); UI matches operator mental model; shareable links encode intent. |

**UI rule:** On first load of `/organizations/{id}/incidents` with **no** discovery query params, the app navigates to (or requests with) **`active=true`** and syncs the URL (see §8). API callers that omit `active` continue to receive **all** incidents sorted by `created_at` desc.

#### Frontend URL convention: `active=all` (not an API parameter)

The incidents list UI uses a **browser-only** query value to represent the “All incidents” view without colliding with the default-active redirect:

| URL state | Meaning | Sent to `GET .../incidents` |
|-----------|---------|-----------------------------|
| No discovery params | Normalize to Active view | `active=true` (after redirect) |
| `active=true` | Active incidents (`OPEN`, `ACKNOWLEDGED`) | `active=true` |
| `active=false` | Resolved and cancelled | `active=false` |
| `active=all` | All statuses | **Omit** `active` and `status` |
| `status=…` | Exact status | `status` only (`active` ignored by API) |

`active=all` is **never** passed to the backend. Direct API clients that omit `active` still receive all statuses; this URL token is not part of the REST contract.

### 5.3 Filter and search parameters

| Parameter | Type | Optional | Default | Validation | Backend |
|-----------|------|----------|---------|------------|---------|
| `active` | boolean | yes | omitted ⇒ no active filter | Parse `true` / `false` only (case-sensitive per Spring); invalid ⇒ **409** `VALIDATION_ERROR` | yes |
| `status` | enum string | yes | omitted | One of `OPEN`, `ACKNOWLEDGED`, `RESOLVED`, `CANCELLED`; invalid ⇒ **409** | yes |
| `severity` | enum string | yes | omitted | One of `SEV1`–`SEV4`; invalid ⇒ **409** | yes |
| `serviceId` | UUID | yes | omitted | Must be valid UUID syntax; invalid UUID ⇒ **400** `VALIDATION_ERROR` | yes |
| `commanderId` | string | yes | omitted | UUID **or** literal `me` (case-sensitive); invalid UUID (and not `me`) ⇒ **400**; `me` resolved to caller id in service layer | yes |
| `mine` | boolean | yes | `false` | Parse `true` / `false` only; invalid ⇒ **409**; if `mine=true` **and** `commanderId` present ⇒ **409** `VALIDATION_ERROR` “Use mine or commanderId, not both” | yes |
| `search` | string | yes | omitted | Trim; if empty after trim, **ignored**; max length **200** after trim; longer ⇒ **409** | yes |

**Not added:** separate `q` alias (use `search` only). Roadmap draft mentioned `q`; this design standardizes on **`search`** for consistency with common REST naming.

### 5.4 Filter combination semantics

- All filters combine with **logical AND**.
- Omitted parameters impose **no constraint** on that dimension.
- **No** comma-separated multi-value `status` or `severity` in Phase 9 (single value only).

### 5.5 Search semantics (backend)

Search applies only when `search` is non-empty after trim.

| Input pattern | Predicate |
|---------------|-----------|
| Matches `(?i)^inc-(\d+)$` | `incident_number = captured integer` (and `organization_id` scope) |
| Matches `^\d+$` only | `incident_number = integer` **OR** `LOWER(title) LIKE '%' || LOWER(term) || '%'` |
| Otherwise | `LOWER(title) LIKE '%' || LOWER(trimmed) || '%'` |

- **Case-insensitive** matching for title (`LOWER` / `ILIKE` on PostgreSQL).
- **Not searched:** `description`, reporter/commander names, service name (only `serviceId` filter).
- **No** fuzzy ranking or relevance score.

### 5.6 Invalid or cross-tenant filter values

| Case | HTTP | Behavior |
|------|------|----------|
| Not org member | **403** | Unchanged |
| `serviceId` valid UUID but service belongs to **another** org | **200** empty page | Filter is conjunctive with `organization_id`; no rows match |
| `commanderId` UUID for user not in org | **200** empty page (typical) | No leak; optional future optimization: skip membership check |
| Malformed UUID in `serviceId` / `commanderId` | **400** | Bean/param validation |
| Invalid enum / boolean | **409** | Align with `OrganizationPostmortemController` invalid `status` |

### 5.7 Empty results

- **200** with `content: []`, `totalElements: 0`, `totalPages: 0` (or `0` pages per Spring Data rules).
- Same envelope as today; no distinct “no such filter” response.

### 5.8 Example requests

```http
GET .../incidents?active=true&page=0&size=20
GET .../incidents?mine=true&severity=SEV1
GET .../incidents?commanderId=me&status=ACKNOWLEDGED
GET .../incidents?serviceId={uuid}&search=checkout
GET .../incidents?search=INC-42
```

---

## 6. Filter and search semantics (summary)

| Topic | Rule |
|-------|------|
| AND vs OR | AND across dimensions |
| Pagination reset | When any filter/search changes, client sets `page=0` |
| `mine` resolution | `mine=true` ⇒ `commander_id = principal.getId()` |
| `commanderId=me` | Same as `mine=true` for commander dimension (mutually exclusive param pair: `mine` + `commanderId`) |
| Whitespace search | Ignored (treated as omitted) |
| `active` vs UI | UI default sends `active=true`; API omits ⇒ all statuses |

---

## 7. Authorization and tenant isolation

| Rule | Detail |
|------|--------|
| List access | Any org role including **VIEWER** — same as today |
| Global `User.role` | Does **not** bypass org membership |
| `mine` / `commanderId=me` | Uses authenticated user id from `UserPrincipal`; no impersonation |
| Search | Cannot return incidents outside `organizationId` path segment |
| Pagination metadata | `totalElements` counts only rows matching org + filters — no cross-tenant leakage |
| Filter option sources | Service and commander dropdowns loaded from **org-scoped** APIs already used on create (`listServices`, `listOrganizationMembers`) |

**403 vs 404:** Path `organizationId` for non-member remains **403**, consistent with existing incident APIs.

---

## 8. Frontend URL and interaction design (proposed)

### 8.1 URL query parameters

Mirror API names (camelCase):

`active`, `status`, `severity`, `serviceId`, `commanderId`, `mine`, `search`, `page`

**Encoding:**

- Booleans: `true` / `false` as strings.
- Omit keys when unset (do not write `active=false` unless user chose “Closed only”).
- `commanderId=me` for “Assigned to me” (preferred in URL over raw UUID for shareable links among same user).

### 8.2 Default view

| Label | Behavior |
|-------|----------|
| **Active incidents** (default) | No discovery params in URL on first visit ⇒ `router.replace` adds `?active=true&page=0` (preserve other future params). Subtitle: “Showing open and acknowledged incidents.” |
| **All incidents** | User clears filters or chooses “All statuses” ⇒ remove `active`, `status`, `mine`, etc. |
| **Closed incidents** | Set `active=false` (label: “Resolved and cancelled”) |

### 8.3 Filter bar layout

Single row (wrap on mobile), consistent with postmortem library (`<select>`, `Input`, `Label`):

| Control | Maps to |
|---------|---------|
| View preset | `active` / all / `active=false` |
| Status | `status` (includes “Any”) |
| Severity | `severity` (“Any”) |
| Service | `serviceId` (“Any service”) |
| Commander | “Anyone” / “Assigned to me” (`commanderId=me`) / specific member (UUID) |
| Search | `search` text input |
| **Clear filters** | Removes all discovery params; returns to default active view |
| Active filter indicator | Text chip or muted line: “Filters applied” when URL differs from default-only `active=true` |

### 8.4 Search debounce

- **300 ms** debounce from last keystroke before updating URL `search` and resetting `page` to `0`.
- Enter key commits immediately.
- Clearing input removes `search` from URL on debounce.

### 8.5 TanStack Query

**Query key (proposed):**

```ts
["incidents", organizationId, { active, status, severity, serviceId, commanderId, mine, search, page, size }, user?.id]
```

Include `user?.id` so `mine` / `me` results refresh on login switch.

**Invalidation:** unchanged — create/PATCH incident invalidates `["incidents", organizationId]` prefix.

Optional: extract `useOrganizationIncidentsQuery` hook in `src/lib/hooks/` (implementation step).

### 8.6 States

| State | UX |
|-------|-----|
| Loading | “Loading incidents…” (existing copy) |
| Error | Destructive message + **Retry** button (`refetch`) — match postmortems page |
| Empty (no rows) | Distinguish **no incidents ever** vs **no matches** (filters active) |
| Pagination | Previous/Next; disabled at bounds; show total count |

### 8.7 Browser navigation

- Filters stored in URL ⇒ **back/forward** restores list state.
- Refresh preserves filters.
- Deep link `?mine=true&severity=SEV1` loads filtered list without resetting to default active view.

### 8.8 Accessibility

- Associate `<Label>` with each control (`for` / `id`).
- Preset and table structure unchanged; filter region `aria-label="Incident filters"` optional.

---

## 9. Database query and indexing considerations

### 9.1 Repository strategy (proposed)

Introduce a single org-scoped query method, e.g.:

- **JPA Specification** (`IncidentListCriteria` + `Specification<Incident>`), or
- **Explicit `@Query`** with nullable parameters and `(:param IS NULL OR column = :param)`.

All predicates include `i.organization.id = :organizationId`.

Pagination: `Pageable` with sort `createdAt DESC, id DESC` — executed in PostgreSQL via Spring Data.

### 9.2 Performance

- Existing composite-friendly indexes on `(organization_id, status|severity|service_id|commander_id)` support selective filters.
- Leading `organization_id` on list query matches index prefixes.
- **Title search** (`LIKE '%term%'`) may not use B-tree indexes; acceptable for MVP org-scoped volumes. No new index required initially.
- **Incident number equality** uses unique `(organization_id, incident_number)` — efficient for `INC-n` / numeric search.

### 9.3 Memory

- Never load full incident list into the JVM; only `Page` of entities/DTOs.

### 9.4 Optional future index (not in Phase 9 migration)

- Expression index on `LOWER(title)` only if profiling shows slow search — document as follow-up, not required for launch.

---

## 10. Backend implementation plan

| Step | Work |
|------|------|
| B1 | `IncidentListCriteria` record (parsed from request params in controller or dedicated parser) |
| B2 | Validation: enums, boolean strings, `mine`+`commanderId` conflict, search length |
| B3 | `IncidentService.listIncidents(orgId, callerId, criteria, pageable)` |
| B4 | Repository/spec query + sort tie-break `id DESC` |
| B5 | Extend `IncidentController.listIncidents` `@RequestParam`s |
| B6 | Update `docs/api.md` |
| B7 | `IncidentControllerIntegrationTest` + focused service/unit tests for criteria parsing |

**Files (expected touch):** `IncidentController`, `IncidentService`, `IncidentRepository` (+ new criteria/spec classes), `docs/api.md`.

---

## 11. Frontend implementation plan

| Step | Work |
|------|------|
| F1 | `parseIncidentListSearchParams` / `buildIncidentListSearchParams` in `src/lib/incident-list-filters.ts` (Vitest) |
| F2 | Extend `listIncidents(organizationId, options)` |
| F3 | Refactor `incidents/page.tsx` — `useSearchParams`, `useRouter`, debounced search |
| F4 | Filter bar UI + clear + empty-state copy |
| F5 | Playwright scenarios (§12) |

---

## 12. Test strategy

### 12.1 Backend integration (`IncidentControllerIntegrationTest` or dedicated class)

| Case | Assertion |
|------|-----------|
| No new params | Same as today — all incidents, pagination |
| `active=true` | Only OPEN/ACKNOWLEDGED |
| `active=false` | Only RESOLVED/CANCELLED |
| `status=OPEN` | Only OPEN; with `active=true` ignored |
| Each `severity` | Correct subset |
| `serviceId` | Only incidents for service; wrong-org service UUID ⇒ empty |
| `commanderId` | UUID filter |
| `commanderId=me` | Incidents commanded by caller |
| `mine=true` | Same as `me` for caller |
| `mine=true` + `commanderId` | **409** |
| `search` title | Case-insensitive substring |
| `search=INC-{n}` | Number match |
| Combined filters | AND semantics |
| Invalid `status` / `active` | **409** |
| Invalid UUID | **400** |
| Outsider org | **403** |
| Stable order | Two incidents same `createdAt` — order by `id` desc |

### 12.2 Frontend unit (Vitest)

| Case | Assertion |
|------|-----------|
| Parse URL → criteria object | Defaults, `me`, booleans |
| Build URL from criteria | Omits empty search |
| `hasActiveDiscoveryFilters` | Detect non-default state |
| Pagination reset helper | Changing filter zeros page |

### 12.3 Playwright (`incidents.spec.ts`)

| Journey | Steps |
|---------|--------|
| Active default | Create OPEN + RESOLVED; open list; expect only active unless “All” |
| Mine | Assign commander to user A; filter “Assigned to me”; see subset |
| Search + filter | Create titled incident; `search` + `severity` combined |

Prerequisites unchanged: API at `NEXT_PUBLIC_API_URL`, Docker Postgres for backend.

---

## 13. Acceptance criteria (objective)

### API

1. `GET .../incidents` without new parameters behaves as today (all statuses, paginated), except **stable sort** adds `id DESC` tie-break.
2. `active=true` returns only `OPEN` and `ACKNOWLEDGED` incidents for the org.
3. `mine=true` returns only incidents where `commanderId` equals the authenticated user.
4. `search` matches title substring and `INC-n` / numeric incident number per §5.5.
5. Combined filters use AND semantics; invalid enum/boolean returns **409**; malformed UUID returns **400**.
6. Non-member receives **403**; no cross-org incidents in `content`.

### UI

7. Default incidents page shows **active** incidents and URL contains `active=true` after initial load.
8. Changing any filter resets to page 0 and updates the URL.
9. Refresh and browser back/forward preserve filters.
10. “Clear filters” restores default active view.
11. Empty filter results show a distinct message from org-wide empty state.
12. VIEWER can use filters read-only.

### Tests

13. New backend integration tests cover §12.1 cases.
14. Vitest covers URL parse/build helpers.
15. Playwright covers §12.3 journeys.

---

## 14. Risks and trade-offs

| Risk | Mitigation |
|------|------------|
| `active` ignored when `status` set — confusing combos | Frontend clears `active` when user picks explicit status; document in API |
| `LIKE '%term%'` scan on large tenants | Org-scoped; monitor; defer trigram index |
| URL default `replace` may surprise users expecting “all” | Clear “All incidents” preset; document in UI copy |
| No `sort` param | Simpler API; revisit if product needs severity-first ordering |
| `mine` vs `commanderId=me` duplication | Document `mine` as preferred boolean; support both for ergonomics |

---

## 15. Decisions and rationale

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Active default | **Option B — frontend** | Backward-compatible API; explicit URLs for sharing |
| Active definition | `OPEN` + `ACKNOWLEDGED` | Matches Phase 5 design and status enum |
| `active` vs `status` | `status` wins; `active` ignored | Avoid 409 on redundant params |
| Search param name | `search` | Clear; roadmap `q` alias not added |
| Commander self-filter | `mine=true` and `commanderId=me` | User request; `mine` readable in URLs |
| Multi-value filters | Deferred | Matches postmortem list style |
| Sort | Fixed `createdAt desc, id desc` | Deterministic pages; no new API surface |
| Wrong-org `serviceId` | Empty list, 200 | No information leakage |

---

## References

- [api.md](api.md) — current list contract (to be updated in implementation)
- [architecture.md](architecture.md)
- [phase-5-incident-design.md](phase-5-incident-design.md) §16, §428–441
- `IncidentController.java`, `IncidentService.java`, `IncidentRepository.java`
- `frontend/src/app/(app)/organizations/[organizationId]/incidents/page.tsx`
- `frontend/src/lib/api/incidents.ts`
- `backend/src/main/resources/db/migration/V6__incidents.sql`
