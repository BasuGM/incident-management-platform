# Phase 8 — Postmortems (design)

**Status:** implemented (Phase 8 complete)  
**Last updated:** 2026-10-09  
**Baseline:** Phase 7 complete → Phase 8 delivery

This document defines **organization-scoped incident postmortems** for Phase 8. It remains the authoritative product and API contract; implementation lives in migration `V9`, backend services/controllers, and frontend types/API/hooks/UI.

### Accepted implementation notes (audit)

- **Unpublish** clears `published_at` / `published_by` (returns to editable draft).
- **Unarchive** sets `status = DRAFT`, clears `archived_at`, and **retains** publication metadata from the prior published state (archived rows were published first). UI may show “Published” timestamps while the badge reads **Draft**.
- **Org list DTO** does not include incident `displayId` or live incident title; library UI uses postmortem title and `incidentId`.

---

## 1. Executive summary

Phase 8 adds a **structured post-incident write-up** tied to exactly **one incident** per organization. Postmortems complement Phase 7 comments (real-time discussion during response) and Phase 6 events (immutable state audit).

- **Cardinality:** at most **one** postmortem per incident (`UNIQUE (incident_id)`).
- **Eligibility:** create and mutate only when the parent incident is **`RESOLVED`**. **`CANCELLED`** incidents are **not** eligible (false alarm / duplicate workflows do not require a formal postmortem in MVP).
- **Lifecycle:** `DRAFT` → `PUBLISHED` → optional `ARCHIVED`; published content is **not** edited in place — **unpublish** returns to `DRAFT` for revision.
- **Content:** plain-text sections (no Markdown/rich text), mirroring comment rendering (`whitespace-pre-wrap`).
- **Action items:** **deferred** as structured entities; MVP uses a single **`correctiveActions`** text section.
- **Audit:** **no** new `IncidentEventType` values and **no** postmortem rows in `app.incident_events` in Phase 8.
- **Tenancy:** same `organizationId` + `incidentId` boundary and **403** cross-tenant semantics as incidents and comments.

---

## 2. Goals

- Capture impact, root cause, resolution, lessons, and follow-up actions after an incident is resolved.
- Enforce organization RBAC and tenant isolation on every operation.
- Reuse existing patterns: Flyway `app` schema, UUID PKs, `OrganizationAuthorizationService`, Java record DTOs, TanStack Query, Playwright.
- Keep `IncidentEvent` limited to incident **state** history; keep `IncidentComment` as **collaboration** during response.

## 3. Explicit non-goals (Phase 8)

- Structured action-item/task tracking (status, assignee, due dates) — defer to a later phase; avoid building a mini task manager.
- Formal multi-step **review/approval** workflow (reviewer roles, approval queues).
- Multiple postmortem versions stored as separate published documents (version table / diff UI).
- Markdown, attachments, @mentions, notifications.
- New `IncidentEvent` types (`POSTMORTEM_*`) or comment timeline integration.
- Org-wide postmortem templates, AI drafting, or export/PDF.
- Changing global `User.role`, organization role model, or incident lifecycle implementation.
- Hard delete of **published** postmortems (use **archive**).

---

## 4. Domain decisions and trade-offs

| Decision | Choice | Rationale | Trade-off |
|----------|--------|-----------|-----------|
| Postmortems per incident | **0 or 1** | Simple mental model; matches “one write-up per incident” in most ops teams. | Teams that want iterative published versions must unpublish → edit → republish (audit via timestamps, not version history). |
| Draft before resolve | **No** | Phase 7 locks comments on terminal incidents and directs long-form learning here; drafting during `OPEN`/`ACKNOWLEDGED` duplicates comments. | Cannot start postmortem skeleton until resolve; acceptable for MVP. |
| `CANCELLED` incidents | **Not eligible** | Cancelled incidents are errors/duplicates; postmortem value is low. | If product later needs “cancellation review,” extend eligibility explicitly. |
| Review state | **Deferred** | OWNER/ADMIN can publish any draft (including others’); no `IN_REVIEW` state. | Less governance for large enterprises; sufficient for MVP RBAC. |
| Published edits | **Unpublish required** | Clear “published” semantics; avoids silent changes to shared document. | Extra step vs in-place edit; matches cautious incident comms. |
| Draft visibility | **All org members** | Same as incident read access; drafts are internal org artifacts. | Sensitive drafts visible to VIEWER; acceptable under org trust model (VIEWER is still a member). |
| Structured action items | **Deferred** | `correctiveActions` text field only. | No tracking completion in-platform until a dedicated actions phase. |
| Delete | **Draft only** (`DELETE`) | Published content is archived, not removed. | Orphan drafts can be deleted by author or OWNER/ADMIN. |
| Incident metadata changes | **No coupling** | Postmortem does not snapshot incident title/severity; UI still shows live incident header separately. | Historical postmortem may reference outdated incident title in prose only. |
| Incident `CANCELLED` after postmortem | **N/A in MVP** | Incidents cannot leave `RESOLVED` today. | If reopen is added later, define whether postmortem is locked or archived. |

---

## 5. Relationship to incidents, events, and comments

| Concern | `IncidentEvent` | `IncidentComment` | `IncidentPostmortem` (Phase 8) |
|--------|-----------------|-------------------|--------------------------------|
| Purpose | State audit | Live discussion | Structured retrospective |
| Typical timing | Throughout | During `OPEN`/`ACKNOWLEDGED` | After `RESOLVED` |
| Mutability | Append-only | Edit + soft-delete | Draft edit; publish lock |
| API | Read-only `.../events` | CRUD `.../comments` | Singleton `.../postmortem` + org list |
| `IncidentEvent` emission | N/A (source) | **None** (Phase 7) | **None** (Phase 8) |

**Future audit (not Phase 8):** If product needs “who published when,” `published_at` / `published_by_id` on the postmortem row suffice for MVP. A dedicated `postmortem_events` table or narrowly scoped incident events can be evaluated in Phase 14 (audit logs) without expanding `IncidentEventType` now.

---

## 6. Lifecycle and state machine

### 6.1 Status enum

| Status | Meaning |
|--------|---------|
| `DRAFT` | Work in progress; editable (subject to RBAC). |
| `PUBLISHED` | Immutable published document for the organization. |
| `ARCHIVED` | Published content retained but hidden from default org list; still readable on incident. |

### 6.2 Allowed transitions

```text
(create)     → DRAFT
DRAFT        → PUBLISHED   (publish)
PUBLISHED    → DRAFT       (unpublish)
PUBLISHED    → ARCHIVED    (archive)
ARCHIVED     → DRAFT       (unarchive — OWNER/ADMIN only)
DRAFT        → (deleted)   (DELETE endpoint — removes row)
```

Illegal transitions return **409** `INVALID_POSTMORTEM_STATUS_TRANSITION`.

### 6.3 Side effects

| Transition | Fields |
|------------|--------|
| → `PUBLISHED` | Set `published_at` (first publish only; retain on republish after unpublish), `published_by_id` = actor; run **publish validation** (§8). |
| → `DRAFT` (unpublish) | Clear `published_at` / `published_by_id` optional — **retain** `published_at` of last publication for display (“Last published …”) **or** clear — **Decision:** retain **last** `published_at` and `published_by_id` in separate columns `last_published_at` / `last_published_by_id` while `status = DRAFT` after unpublish. Simpler MVP: single `published_at` / `published_by_id` nulled on unpublish; `updated_at` tracks activity. |
| → `ARCHIVED` | Set `archived_at`; keep `published_at` / `published_by_id`. |
| → `DRAFT` from `ARCHIVED` | Clear `archived_at`. |

**MVP simplification:** use `published_at` and `published_by_id` only while `status = PUBLISHED`. On unpublish, null them. UI shows “Previously unpublished draft” without last-published metadata until republished.

### 6.4 Who may transition

| Transition | OWNER | ADMIN | MEMBER | VIEWER |
|------------|-------|-------|--------|--------|
| Create draft | yes | yes | yes | no |
| Edit draft fields | yes* | yes* | yes† | no |
| Publish | yes | yes | yes‡ | no |
| Unpublish | yes | yes | yes‡ | no |
| Archive | yes | yes | no | no |
| Unarchive | yes | yes | no | no |
| Delete draft | yes | yes | yes§ | no |

\*OWNER/ADMIN may edit **any** draft (moderation).  
†MEMBER may edit **only their own** draft (`author_id` match).  
‡MEMBER may publish/unpublish **only when `author_id` matches**; OWNER/ADMIN may publish/unpublish **any** draft.  
§MEMBER may delete **only own** draft; OWNER/ADMIN may delete any draft.

**Review workflow:** intentionally omitted. Publishing is a single explicit action with validation, not a multi-party approval gate.

---

## 7. Content model

### 7.1 Entity: `IncidentPostmortem`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | UUID | yes | PK |
| `organization_id` | UUID | yes | Denormalized; must match incident |
| `incident_id` | UUID | yes | **Unique** — one postmortem per incident |
| `author_id` | UUID | yes | Creator; immutable |
| `status` | enum | yes | `DRAFT`, `PUBLISHED`, `ARCHIVED` |
| `title` | string | no | Max **200**; default at create: `"Postmortem: {incident.title}"` (server-generated, truncating incident title if needed) |
| `summary` | text | no* | Max **10000** chars |
| `impact` | text | no | Max **10000** |
| `root_cause` | text | no* | Max **10000** |
| `resolution` | text | no | Max **10000** |
| `lessons_learned` | text | no | Max **10000** |
| `corrective_actions` | text | no | Max **10000**; free-form bullets, not structured items |
| `created_at` | timestamptz | yes | |
| `updated_at` | timestamptz | yes | |
| `published_at` | timestamptz | no | Set when entering `PUBLISHED`; cleared on unpublish |
| `published_by_id` | UUID | no | FK → users |
| `archived_at` | timestamptz | no | Set when entering `ARCHIVED` |

\*Required **on publish** only (§8).

### 7.2 Plain text and validation

| Rule | Value |
|------|--------|
| Encoding | Plain text; preserve `\n`; trim leading/trailing whitespace per field on write |
| Empty fields | Allowed on **draft** (including all optional sections empty) |
| Whitespace-only | Treated as empty (null stored or empty string — pick empty string for NOT NULL columns) |
| Rich text | **Not supported** (same as comments) |
| XSS | React text nodes + `whitespace-pre-wrap` only |

### 7.3 Publish validation

When transitioning to `PUBLISHED`, after trim:

| Field | Rule |
|-------|------|
| `summary` | Required; min **1** non-whitespace character |
| `root_cause` | Required; min **1** non-whitespace character |
| All other sections | Optional |

Failure → **409** `POSTMORTEM_PUBLISH_VALIDATION_FAILED` with `details` listing fields (mirror Bean Validation style in `ApiErrorResponse`).

### 7.4 Structured action items (deferred)

A future phase may introduce `app.postmortem_action_items` with `description`, `owner_user_id`, `status`, `due_date`. Phase 8 **does not** add this table. Teams list actions in `corrective_actions` as plain text.

---

## 8. Authorization matrix (summary)

All operations require `requireIncidentInOrganization` (or org list scope) unless noted.

| Action | OWNER | ADMIN | MEMBER | VIEWER |
|--------|-------|-------|--------|--------|
| Read postmortem (any status) | yes | yes | yes | yes |
| Create draft (`RESOLVED` only) | yes | yes | yes | no |
| Update draft content | any draft | any draft | own draft only | no |
| Publish | any draft | any draft | own draft only | no |
| Unpublish | any published | any published | own published only | no |
| Archive | yes | yes | no | no |
| Unarchive | yes | yes | no | no |
| Delete draft row | any draft | any draft | own draft only | no |
| Org list `GET .../postmortems` | yes | yes | yes | yes |

Global `User.role` does **not** bypass organization checks.

**Draft vs published read:** no distinction — all members read all statuses. UX may emphasize status badges.

---

## 9. Incident integration

1. **Relationship:** `incident_postmortems.incident_id` → `incidents.id`; `organization_id` copied from incident on create.
2. **Eligibility:** `incident.status == RESOLVED` for `POST`, `PATCH`, publish/unpublish/archive/delete. Reads allowed whenever incident is readable (any status) if a postmortem row exists (edge case: if lifecycle rules change in future, re-evaluate).
3. **`CANCELLED`:** `POST` / writes return **409** `INCIDENT_NOT_ELIGIBLE_FOR_POSTMORTEM`.
4. **Non-resolved without row:** `GET .../postmortem` → **404** `POSTMORTEM_NOT_FOUND`; UI shows eligibility message instead of calling create.
5. **Comments:** remain separate; no merge into postmortem body.
6. **Incident field changes:** do not auto-update postmortem `title` or sections.
7. **`IncidentEvent`:** postmortem operations do **not** call `IncidentEventRecorder`.

---

## 10. Database design (proposed — not migrated)

Migration: **`V9__incident_postmortems.sql`** (next after `V8`).

```sql
CREATE TABLE app.incident_postmortems (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES app.organizations (id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES app.incidents (id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES app.users (id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary TEXT NOT NULL DEFAULT '',
    impact TEXT NOT NULL DEFAULT '',
    root_cause TEXT NOT NULL DEFAULT '',
    resolution TEXT NOT NULL DEFAULT '',
    lessons_learned TEXT NOT NULL DEFAULT '',
    corrective_actions TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    published_by_id UUID REFERENCES app.users (id) ON DELETE RESTRICT,
    archived_at TIMESTAMPTZ,
    CONSTRAINT uq_incident_postmortems_incident UNIQUE (incident_id),
    CONSTRAINT chk_incident_postmortems_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_incident_postmortems_title_length CHECK (char_length(title) <= 200),
    CONSTRAINT chk_incident_postmortems_summary_length CHECK (char_length(summary) <= 10000),
    CONSTRAINT chk_incident_postmortems_impact_length CHECK (char_length(impact) <= 10000),
    CONSTRAINT chk_incident_postmortems_root_cause_length CHECK (char_length(root_cause) <= 10000),
    CONSTRAINT chk_incident_postmortems_resolution_length CHECK (char_length(resolution) <= 10000),
    CONSTRAINT chk_incident_postmortems_lessons_length CHECK (char_length(lessons_learned) <= 10000),
    CONSTRAINT chk_incident_postmortems_actions_length CHECK (char_length(corrective_actions) <= 10000)
);

CREATE INDEX idx_incident_postmortems_organization_status_published
    ON app.incident_postmortems (organization_id, status, published_at DESC NULLS LAST);

CREATE INDEX idx_incident_postmortems_organization_created
    ON app.incident_postmortems (organization_id, created_at DESC);
```

**Deletion behavior:**

- `ON DELETE CASCADE` from `incidents` and `organizations` (postmortem removed with incident).
- Application **DELETE** only in `DRAFT` (row removal); published uses `ARCHIVED` status.

**`DatabaseCleaner` (tests):** delete `incident_postmortems` before `incident_comments` before `incident_events` before `incidents`.

**Concurrency:** one row per incident; unique constraint prevents duplicate creates. `POST` when row exists → **409** `POSTMORTEM_ALREADY_EXISTS`.

---

## 11. REST API

Base paths:

```text
/api/v1/organizations/{organizationId}/incidents/{incidentId}/postmortem
/api/v1/organizations/{organizationId}/postmortems
```

### 11.1 `GET .../incidents/{incidentId}/postmortem`

- **Auth:** any org member.
- **Response:** **200** + `IncidentPostmortemResponse`.
- **404** `POSTMORTEM_NOT_FOUND` when no row (member + valid incident).
- Cross-tenant: **403**.

### 11.2 `POST .../incidents/{incidentId}/postmortem`

- **Auth:** OWNER, ADMIN, MEMBER.
- **Preconditions:** incident `RESOLVED`; no existing postmortem.
- **Body:** optional `CreateIncidentPostmortemRequest` — all section fields optional; server sets `authorId`, `status = DRAFT`, default `title`.
- **Response:** **201** + `IncidentPostmortemResponse`.
- **409:** `INCIDENT_NOT_ELIGIBLE_FOR_POSTMORTEM`, `POSTMORTEM_ALREADY_EXISTS`.

### 11.3 `PATCH .../incidents/{incidentId}/postmortem`

- **Auth:** draft edit rules (§6.4).
- **Preconditions:** `status == DRAFT`; incident still `RESOLVED`.
- **Body:** `UpdateIncidentPostmortemRequest` — partial update; only sent fields apply (PATCH semantics like incidents/services).
- **Response:** **200** + updated DTO.
- **409:** `POSTMORTEM_NOT_EDITABLE` (not draft), `INCIDENT_NOT_ELIGIBLE_FOR_POSTMORTEM`.

### 11.4 `POST .../incidents/{incidentId}/postmortem/publish`

- **Auth:** publish rules (§6.4).
- **Body:** empty or `{}`.
- **Response:** **200** + `IncidentPostmortemResponse` with `status = PUBLISHED`.
- **409:** publish validation, invalid transition.

### 11.5 `POST .../incidents/{incidentId}/postmortem/unpublish`

- **Auth:** unpublish rules.
- **Response:** **200**; `status = DRAFT`; clear `published_at` / `published_by_id`.

### 11.6 `POST .../incidents/{incidentId}/postmortem/archive`

- **Auth:** OWNER, ADMIN only.
- **Preconditions:** `status == PUBLISHED`.
- **Response:** **200**; `status = ARCHIVED`; set `archived_at`.

### 11.7 `POST .../incidents/{incidentId}/postmortem/unarchive`

- **Auth:** OWNER, ADMIN only.
- **Preconditions:** `status == ARCHIVED`.
- **Response:** **200**; `status = DRAFT` (not auto-published); clear `archived_at`.

### 11.8 `DELETE .../incidents/{incidentId}/postmortem`

- **Auth:** delete rules (§6.4).
- **Preconditions:** `status == DRAFT`.
- **Response:** **204 No Content**.

### 11.9 `GET .../organizations/{organizationId}/postmortems`

- **Auth:** any org member.
- **Query:** `page` (default `0`), `size` (default `20`, max `100`), optional `status` (`PUBLISHED` default for list; allow `DRAFT`, `ARCHIVED`, or `all` — recommend `status` filter enum; default **`PUBLISHED`** for “learning library” view).
- **Order:** `published_at DESC`, then `created_at DESC` for published; `created_at DESC` for drafts.
- **Response:** `IncidentPostmortemSummaryPageResponse` — lightweight rows: `id`, `incidentId`, `incidentDisplayId`, `incidentTitle`, `status`, `title`, `summary` truncated (e.g. 200 chars), `authorId`, author display fields, `publishedAt`, `createdAt`.

### 11.10 DTOs

**`IncidentPostmortemResponse`:** all persisted fields + author/publisher display names + `incidentId`, `organizationId`.

**`CreateIncidentPostmortemRequest`:** optional `title`, `summary`, `impact`, `rootCause`, `resolution`, `lessonsLearned`, `correctiveActions` (camelCase JSON).

**`UpdateIncidentPostmortemRequest`:** same optional fields; no `status` in body (lifecycle via dedicated POST actions).

### 11.11 Error codes

| Code | HTTP | When |
|------|------|------|
| `VALIDATION_ERROR` | 400 | Bean validation |
| `FORBIDDEN` | 403 | Role / tenant |
| `POSTMORTEM_NOT_FOUND` | 404 | No row (singleton GET) |
| `POSTMORTEM_ALREADY_EXISTS` | 409 | Second create |
| `INCIDENT_NOT_ELIGIBLE_FOR_POSTMORTEM` | 409 | Not `RESOLVED` |
| `POSTMORTEM_NOT_EDITABLE` | 409 | Patch/delete on non-draft or wrong author |
| `INVALID_POSTMORTEM_STATUS_TRANSITION` | 409 | Illegal lifecycle |
| `POSTMORTEM_PUBLISH_VALIDATION_FAILED` | 409 | Missing summary/root cause |

Wrong-org incident/postmortem: **403** (no 404 leak), consistent with Phase 5–7.

---

## 12. Backend structure (planned)

```text
com.example.incidentmanagement.incident/
  IncidentPostmortem.java
  IncidentPostmortemStatus.java
  IncidentPostmortemRepository.java
  IncidentPostmortemService.java
  IncidentPostmortemController.java
  OrganizationPostmortemController.java   // org list only, or nested in same controller
  dto/
    CreateIncidentPostmortemRequest.java
    UpdateIncidentPostmortemRequest.java
    IncidentPostmortemResponse.java
    IncidentPostmortemSummaryResponse.java
    IncidentPostmortemPageResponse.java
```

Thin controllers; authorization and lifecycle in `IncidentPostmortemService` using `OrganizationAuthorizationService`.

---

## 13. Frontend UX (planned)

### 13.1 Entry point

On **incident detail** (`/organizations/[organizationId]/incidents/[incidentId]`):

```text
Incident header + status actions
Metadata grid
Postmortem section          ← Phase 8 (new)
Comments section            ← Phase 7 (unchanged)
Timeline section            ← Phase 6 (unchanged)
```

### 13.2 States

| Incident status | Postmortem UI |
|-----------------|---------------|
| Not `RESOLVED` | Short message: “Postmortem can be created after the incident is resolved.” No create button. |
| `RESOLVED`, no row | “Create postmortem” (MEMBER+) → `POST` then edit form. |
| `DRAFT` | Badge **Draft**; sectioned form (title + textareas); Save (PATCH); Publish (with validation errors surfaced); Delete draft if permitted. |
| `PUBLISHED` | Badge **Published**; read-only sections; Unpublish if permitted; Archive (OWNER/ADMIN). |
| `ARCHIVED` | Badge **Archived**; read-only; Unarchive (OWNER/ADMIN). |

### 13.3 Editing experience

- One card/section per field (Summary, Impact, Root cause, Resolution, Lessons learned, Corrective actions).
- Character counters toward **10000** per field; title **200**.
- Loading skeleton on first `GET`; error + Retry; empty draft encourages filling summary/root cause before publish.
- Permission-aware buttons via `postmortem-rbac.ts` helpers (mirror `comment-rbac.ts`).

### 13.4 Org-level discovery (optional MVP+)

Link from organization overview: **Postmortems** → `/organizations/[organizationId]/postmortems` listing published (and filter). If scope pressure, ship incident-embedded UX first; org list in Step 6b.

**Recommendation for implementation sequence:** incident-embedded UX **required**; org list **included** in Phase 8 (backend + simple table page) because query API is designed.

### 13.5 TanStack Query keys

| Key | Usage |
|-----|--------|
| `["incident-postmortem", organizationId, incidentId, user?.id]` | Singleton |
| `["organization-postmortems", organizationId, { status, page, size }, user?.id]` | List |

Invalidate postmortem key on PATCH/publish/unpublish/archive/delete; invalidate list on publish/archive.

### 13.6 Accessibility

- Labels for every textarea; status badges with text (not color-only).
- Publish/Unpublish/Archive use confirm dialogs for destructive-ish actions.

---

## 14. Testing strategy

### 14.1 Backend unit (`IncidentPostmortemServiceTest`)

- Publish validation (summary/root cause required).
- Status transition matrix (valid/invalid).
- MEMBER edit own vs other draft forbidden.
- OWNER edit/publish any draft.
- Delete draft author vs other MEMBER.
- Default title on create.

### 14.2 Backend integration

- Create on `RESOLVED` → 201; on `OPEN` / `CANCELLED` → 409.
- Unique constraint: duplicate POST → 409.
- Cross-org GET/POST → 403.
- Singleton GET 404 when missing.
- PATCH only in `DRAFT`; published PATCH → 409.
- Publish → unpublish → edit → republish flow.
- Archive/unarchive OWNER vs MEMBER 403.
- Org list pagination, `status` filter, tenant isolation.
- `DatabaseCleaner` order includes postmortems.
- Verify **no** rows inserted into `incident_events` on postmortem operations.

### 14.3 Frontend E2E (Playwright)

- Resolve incident → create draft → save → publish (validation error when summary empty).
- VIEWER cannot create; can read published.
- MEMBER cannot edit another user’s draft.
- ADMIN publishes another user’s draft.
- Org list shows published postmortem.
- Tenant switch does not show other org’s postmortem.
- Terminal eligibility messaging on open incident.

---

## 15. Implementation sequence

| Step | Scope | Validation | Done when |
|------|--------|------------|-----------|
| 0 | Design doc + plan updates | Review | This document approved |
| 1 | `V9` migration, entity, repository, persistence tests, `DatabaseCleaner` | `./gradlew test` (new tests green) | Table matches §10 |
| 2 | `IncidentPostmortemService` — eligibility, RBAC, lifecycle, validation | Unit tests | All §14.1 cases pass |
| 3 | REST controllers + DTOs + integration tests | `./gradlew test` | API matches §11 |
| 4 | Update `docs/api.md`, `docs/database.md` with implemented endpoints | Doc review | Docs match code |
| 5 | Frontend types + `src/lib/api/incident-postmortems.ts` + hooks | `npm run typecheck` | API client complete |
| 6 | Incident detail Postmortem section + `postmortem-rbac.ts` | Manual + lint | UX matches §13 |
| 7 | Org postmortems list page + nav link | `npm run build` | List uses paginated API |
| 8 | Playwright E2E | `npx playwright test` | §14.3 flows pass |
| 9 | Final audit, `development-plan.md` sign-off | Full test suite | Phase 8 marked complete |

---

## 16. Open decisions

**None** for MVP architecture. Explicit choices: one postmortem per incident, `RESOLVED`-only writes, three statuses, unpublish for revisions, plain text, no `IncidentEvent` coupling, structured action items deferred.

---

## 17. References

- [phase-5-incident-design.md](phase-5-incident-design.md) — incident lifecycle, terminal states
- [phase-7-comments-design.md](phase-7-comments-design.md) — collaboration vs audit, RBAC patterns
- `OrganizationAuthorizationService`, `IncidentCommentService`, `IncidentCommentController`
- `frontend/src/lib/comment-rbac.ts`, incident detail page layout

---

## 18. Implementation status

**Phase 8 implementation — NOT STARTED.**  
**Phase 8 Step 0 (design) — complete when this document and plan updates are merged.**
