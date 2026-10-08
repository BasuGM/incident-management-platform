# Phase 7 — Incident comments & collaboration (design)

**Status:** design only — not implemented  
**Last updated:** 2026-10-07  
**Baseline:** `main` @ Phase 6 complete (`da373a0`)

This document defines **organization-scoped incident comments** for Phase 7. It does not implement code, migrations, APIs, or UI.

---

## 1. Executive summary

Phase 7 adds **user-authored collaboration** on incidents via `IncidentComment`, separate from the **immutable audit timeline** (`IncidentEvent`) delivered in Phase 6.

- Comments are **mutable** (edit + soft-delete) under explicit RBAC.
- Comments are **tenant-scoped** by `organizationId` + `incidentId`, authorized through the same incident boundary as `GET /incidents/{incidentId}`.
- **No** notifications, @mentions, attachments, Markdown, or new `IncidentEventType` values in Phase 7.
- Comments appear in a dedicated **Comments** section on incident detail; they do **not** merge into the Phase 6 event timeline.

---

## 2. Goals

- Allow OWNER, ADMIN, and MEMBER to discuss an incident in plain text.
- Allow VIEWER to read comments (read-only).
- Preserve strict organization isolation (**403** for cross-tenant access, consistent with incidents and services).
- Reuse `OrganizationAuthorizationService`, JWT `UserPrincipal`, DTO patterns, Flyway, TanStack Query, and Playwright conventions.
- Keep `IncidentEvent` append-only and semantically limited to incident **state/history**, not chat.

## 3. Explicit non-goals (Phase 7)

- @mentions, watchers, or notification delivery (Phase 10+).
- File attachments or rich text / Markdown rendering.
- Comment rows in `app.incident_events` or new audit event types for comments.
- WebSockets, SSE, or polling for live comment streams.
- Postmortem entities (Phase 8).
- Global audit log product (Phase 13).
- Changing organization role model or global `User.role`.

---

## 4. Relationship to Phase 6 (`IncidentEvent`)

| Concern | `IncidentEvent` (Phase 6) | `IncidentComment` (Phase 7) |
|--------|---------------------------|-----------------------------|
| Purpose | System audit / state history | Human collaboration |
| Authorship | Server (`actor_id` from business operation) | User (`author_id` from authenticated principal) |
| Mutability | Append-only | Editable + soft-deletable |
| API | Read-only `GET .../events` | CRUD subset under `.../comments` |
| UI | Timeline (newest first) | Comments thread (chronological) |
| Generation | `IncidentService` + `IncidentEventRecorder` | `IncidentCommentService` (new) |

**Timeline integration (decision):** **Option A — separate surfaces.** Comments and timeline are adjacent on incident detail but backed by different APIs and UI components. Comment create/edit/delete does **not** emit `IncidentEvent` rows in Phase 7.

**Rationale:** Adding `COMMENT_*` event types would blur audit semantics (state changes vs conversation) and encourage duplicating comment bodies in JSONB. Future phases may add **notification** or **activity feed** products without overloading `IncidentEvent`.

---

## 5. Domain model: `IncidentComment`

### 5.1 Fields

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `id` | UUID | yes | PK; client never assigns |
| `organization_id` | UUID | yes | Denormalized tenant key; must match incident’s organization |
| `incident_id` | UUID | yes | Parent incident |
| `author_id` | UUID | yes | Set from authenticated user on create; immutable |
| `body` | TEXT | yes* | Plain text; see §8 (*empty when soft-deleted) |
| `created_at` | TIMESTAMPTZ | yes | Set on insert |
| `updated_at` | TIMESTAMPTZ | yes | Bumped on successful body edit |
| `deleted_at` | TIMESTAMPTZ | no | Null = active; non-null = soft-deleted |

**No** `edited_by`, revision table, or `version` column in Phase 7 (last-write-wins; see §16).

### 5.2 Edit policy (decision)

- **Authors** may edit **their own** active comments (`PATCH` body).
- **OWNER/ADMIN** may **not** edit another user’s comment text (avoids silent rewriting of someone else’s words).
- **OWNER/ADMIN** may **soft-delete** any comment (moderation).

### 5.3 Delete policy (decision)

- **Soft delete** via `deleted_at` (and clearing `body` to empty string or a fixed tombstone constant in DB — API returns a structured tombstone, not the original text).
- **Author** may soft-delete their own active comment.
- **OWNER/ADMIN** may soft-delete any comment.
- **MEMBER** cannot delete another member’s comment.
- **VIEWER** cannot delete.

**Rationale:** Incident discussions may matter for post-incident review; soft delete supports moderation and “remove harmful content” without pretending the thread never existed. Hard delete is deferred unless compliance requires purging (future admin tooling).

### 5.4 Edit history (decision)

- **No** `IncidentEvent` on comment edit/delete.
- **No** separate revision table in Phase 7.
- `updated_at` is the only client-visible edit signal.

---

## 6. Authorship

- On **create**, `author_id` = `UserPrincipal.getId()` after `requireRole(..., OWNER, ADMIN, MEMBER)`.
- Request body must **not** include `authorId`, `organizationId`, `incidentId`, or timestamps.
- API responses include author display fields mirroring timeline/events:

  `authorId`, `authorEmail`, `authorFirstName`, `authorLastName`

- Use existing `User` entity / membership; no parallel identity system.

---

## 7. Tenant isolation

All operations validate:

1. Caller is a member of `organizationId` (or stricter role for writes).
2. `incidentId` exists **in that organization** (`requireIncidentInOrganization`).
3. For comment-by-id operations, comment row matches `(organizationId, incidentId, commentId)`.

**Cross-tenant behavior (match incidents/services):**

| Scenario | HTTP |
|----------|------|
| Unauthenticated | **401** |
| Not org member | **403** |
| Wrong org in path for incident/comment | **403** (`ForbiddenException`) |
| Valid org, unknown incident | **403** |
| Valid org + incident, unknown `commentId` | **403** |
| Valid org + incident, comment belongs to **another** incident | **403** |

Do **not** return **404** for cross-org IDs (Phase 5 convention).

---

## 8. RBAC matrix

| Action | OWNER | ADMIN | MEMBER | VIEWER |
|--------|-------|-------|--------|--------|
| List comments | yes | yes | yes | yes |
| Create comment | yes | yes | yes | no |
| Edit own comment | yes* | yes* | yes* | no |
| Edit others’ comment | no | no | no | no |
| Delete own comment | yes | yes | yes | no |
| Delete others’ comment | yes | yes | no | no |

\*Only while incident allows comment writes (§15) and comment is not soft-deleted.

**Read vs write:** Incident read access (all roles) ≠ comment creation (excludes VIEWER). Aligns with `canUpdateIncident` vs read-only viewer on incident detail.

Frontend helpers (planned):

- `canCreateComment(role)` → `role !== 'VIEWER'`
- `canEditComment(role, comment, currentUserId)` → non-viewer, author match, not deleted, incident writable
- `canDeleteComment(role, comment, currentUserId)` → author or OWNER/ADMIN, not viewer, incident writable for author-delete; OWNER/ADMIN always for moderation delete

---

## 9. Incident lifecycle interaction

**Decision:** Comment **reads** allowed for all statuses (`OPEN`, `ACKNOWLEDGED`, `RESOLVED`, `CANCELLED`).

Comment **writes** (create, edit, delete) allowed only when incident is **not terminal**:

- `OPEN`, `ACKNOWLEDGED` → writes allowed (subject to RBAC).
- `RESOLVED`, `CANCELLED` → writes return **409** with `INCIDENT_NOT_EDITABLE` (reuse existing code/message family) or dedicated `COMMENT_NOT_ALLOWED` if clearer in tests.

**Rationale:** Matches Phase 5 incident field edit lock; closed incidents remain readable for context. Long-form learning belongs in Phase 8 postmortems, not unbounded chat on closed incidents.

---

## 10. Database design (`V8__incident_comments.sql`)

Next migration after `V7__incident_events.sql`.

```sql
CREATE TABLE app.incident_comments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES app.organizations (id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES app.incidents (id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES app.users (id) ON DELETE RESTRICT,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_incident_comments_body_length CHECK (char_length(body) <= 5000)
);

CREATE INDEX idx_incident_comments_org_incident_created
    ON app.incident_comments (organization_id, incident_id, created_at ASC, id ASC);

CREATE INDEX idx_incident_comments_org_incident_active_created
    ON app.incident_comments (organization_id, incident_id, created_at ASC, id ASC)
    WHERE deleted_at IS NULL;
```

**FK notes:**

- `incident_id` → `incidents` **ON DELETE CASCADE** (comments die with incident; consistent with events).
- `organization_id` → `organizations` **ON DELETE CASCADE**.
- `author_id` → `users` **ON DELETE RESTRICT** (same as reporter/commander on incidents).

**Integrity:** Application must set `organization_id` from the incident’s organization on create (DB could add a trigger later; not required in Phase 7).

**DatabaseCleaner (tests):** delete `incident_comments` before `incident_events` before `incidents`.

---

## 11. Body validation

| Rule | Value |
|------|--------|
| Max length | **5000** characters (`char_length` / Bean Validation `@Size(max = 5000)`) |
| Min length | **1** non-whitespace character after trim |
| Trim | Leading/trailing whitespace trimmed on create/update |
| Whitespace-only | Rejected (**400** `VALIDATION_ERROR`) |
| Line breaks | Allowed (plain text); preserve `\n` in storage and API |
| Markdown | **Not supported** in Phase 7 |

---

## 12. XSS / content security (frontend)

- Render comment `body` as **plain text** only.
- Use React text nodes or equivalent; **never** `dangerouslySetInnerHTML` for comment body.
- Preserve line breaks via CSS `white-space: pre-wrap` or split lines into `<p>`/`<br>` without interpreting HTML/Markdown.
- If Markdown is added later, require a sanitizer allowlist and server-side length limits.

---

## 13. REST API

Base path:

```text
/api/v1/organizations/{organizationId}/incidents/{incidentId}/comments
```

### 13.1 `GET .../comments`

- **Auth:** org member (any role).
- **Query:** `page` (default `0`), `size` (default `20`, max `100`, same clamp as incidents/events).
- **Order:** `createdAt ASC`, `id ASC` (chronological conversation).
- **Response:** `IncidentCommentPageResponse` — `content`, `page`, `size`, `totalElements`, `totalPages`.
- **Soft-deleted comments:** Included with `deleted: true` and no `body` (or null `body`) so thread ordering/holes are visible; UI shows “Comment removed” (exact copy in implementation).

### 13.2 `POST .../comments`

- **Auth:** OWNER, ADMIN, MEMBER; incident writable.
- **Body:** `{ "body": "..." }` only.
- **Response:** **201** + `IncidentCommentResponse`.

### 13.3 `PATCH .../comments/{commentId}`

- **Auth:** author only; comment active; incident writable.
- **Body:** `{ "body": "..." }` only.
- **Response:** **200** + updated `IncidentCommentResponse`; `updatedAt` changes.

### 13.4 `DELETE .../comments/{commentId}`

- **Auth:** author (own) or OWNER/ADMIN (any); incident writable for author-delete; OWNER/ADMIN may delete on terminal? **No** — no deletes on terminal incidents for anyone (consistent write lock).
- **Behavior:** soft delete (`deleted_at = now()`, clear body).
- **Response:** **204 No Content** (match DELETE conventions elsewhere if present; otherwise 204).

### 13.5 Error codes (illustrative)

| Code | When |
|------|------|
| `VALIDATION_ERROR` | Empty/too long body |
| `FORBIDDEN` | Role / tenant |
| `INCIDENT_NOT_EDITABLE` | Terminal incident write |
| `COMMENT_NOT_EDITABLE` | Edit/delete on soft-deleted or non-author |

Use `ApiErrorResponse` / `GlobalExceptionHandler`; no comment-specific error envelope.

---

## 14. DTOs

### `IncidentCommentResponse`

```text
id
incidentId
organizationId
authorId
authorEmail
authorFirstName
authorLastName
body              // null or omitted when deleted
deleted           // boolean
createdAt
updatedAt
```

### `IncidentCommentPageResponse`

Same pagination shape as `IncidentPageResponse` / `IncidentEventPageResponse`.

**N+1:** List query uses `@EntityGraph(attributePaths = {"author"})` or equivalent join fetch (mirror `IncidentEvent`).

---

## 15. Concurrency

**Decision:** **Last-write-wins** on `PATCH`; no `If-Match`, no version column in Phase 7.

Document in API that simultaneous edits may overwrite without conflict detection.

---

## 16. Backend structure (planned)

```text
com.example.incidentmanagement.incident/
  IncidentComment.java
  IncidentCommentRepository.java
  IncidentCommentService.java
  IncidentCommentController.java
  dto/
    CreateIncidentCommentRequest.java
    UpdateIncidentCommentRequest.java
    IncidentCommentResponse.java
    IncidentCommentPageResponse.java
```

- Thin controller; validation on DTOs; authorization in service via `OrganizationAuthorizationService`.
- **Do not** route comment persistence through `IncidentEventRecorder`.

---

## 17. Frontend UX (planned)

**Layout on incident detail (recommended):**

```text
Incident header + actions
Incident metadata grid
Comments section          ← Phase 7
Timeline section          ← Phase 6 (unchanged)
```

### Comments section

- Heading: **Comments**
- **Composer** (textarea + Submit) when `canCreateComment` and incident writable; hidden for VIEWER/terminal.
- **Character counter** toward 5000.
- **List:** chronological (oldest at top); **Load more** at bottom fetching **older** pages if using reverse pagination — **or** standard page 0 = oldest chunk and load more appends newer if API returns ASC pages from start. **Recommended:** page 0 = oldest comments; “Load more” fetches `page + 1` and **appends** to the bottom (consistent with ASC API).

Actually with ASC order, page 0 is oldest messages. For long threads, users scroll down; “Load more” loads **next page** of older→newer sequence (page 1 = next chronological segment). Same pattern as timeline but direction inverted.

- **Edit:** inline or small modal for author; Save/Cancel.
- **Delete:** confirm dialog; tombstone rendering for deleted rows.
- **States:** loading, empty (“No comments yet”), error + Retry.
- **TanStack Query key:** `["incident-comments", organizationId, incidentId, pageSize, user?.id]`; invalidate on create/edit/delete; invalidate on incident status change if terminal.

### VIEWER / MEMBER / ADMIN

Documented in §8; UI hides controls accordingly.

---

## 18. Extension points (not implemented)

- **Notifications:** `comment.created` domain hook in service (no-op interface or TODO) for Phase 10.
- **Mentions:** defer; plain text only; no `@` parsing in Phase 7.
- **Attachments:** defer.
- **Activity in timeline:** if product later wants it, prefer a separate **activity feed** or narrowly scoped event types with comment **id** only (no body duplication).

---

## 19. Test strategy

### Backend (integration + unit)

- Create/list pagination and ASC order
- Create auth: MEMBER yes, VIEWER 403
- Update own vs other user 403
- Delete own vs other MEMBER 403; OWNER delete other’s
- Cross-org list/create 403
- Wrong `commentId` / wrong incident 403
- Validation: empty, whitespace, max length
- Terminal incident: read OK, write 409
- Soft-deleted: no edit; tombstone in list
- `author_id` from principal, not request
- Transaction: comment persist independent of `IncidentEvent` (no accidental event rows)

### Frontend (Playwright)

- Render thread; multiline safe display
- Create/edit/delete happy paths
- VIEWER read-only
- Org/incident switch does not show stale comments
- User switch (logout/login) does not leak prior user’s cached comments
- Terminal incident hides composer

---

## 20. Implementation sequence

| Step | Deliverable |
|------|-------------|
| 1 | `V8` migration, entity, repository, persistence tests, `DatabaseCleaner` |
| 2 | `IncidentCommentService` + authorization + lifecycle rules |
| 3 | REST controller + DTOs + validation |
| 4 | Backend integration/controller tests |
| 5 | Frontend types + `getIncidentComments` / mutations in `incidents.ts` or `comments.ts` |
| 6 | Comment UI components + incident detail integration |
| 7 | Playwright E2E |
| 8 | Security/accessibility pass (plain text, RBAC, tenant keys) |
| 9 | Phase 7 sign-off |

---

## 21. Open decisions

**No open architectural decisions remain for Phase 7.** Policies above are explicit: soft delete, separate from `IncidentEvent`, chronological ASC comments, terminal incident write lock, last-write-wins edits, OWNER/ADMIN moderate via delete only.

---

## 22. References

- Phase 5 incidents: [phase-5-incident-design.md](phase-5-incident-design.md)
- Phase 6 events: `docs/database.md`, `docs/api.md` (events endpoint)
- RBAC: `OrganizationAuthorizationService`, `frontend/src/lib/organization-rbac.ts`

---

## 23. Implementation status

**Phase 7 implementation completed** (2026-10-09). The authoritative design above is unchanged; delivered behavior matches Sections 1–21, including intentional deferrals (no comment `IncidentEvent` types, no partial active-comment index in `V8` unless performance requires it later).
