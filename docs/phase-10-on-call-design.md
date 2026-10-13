# Phase 10 — On-call schedules (design)

**Status:** **signed off** (2026-10-13) — MVP implemented in tree; automated verification recorded in [development-plan.md](development-plan.md) Phase 10 Step 10. Not a production deployment sign-off.  
**Sign-off owner:** Not recorded.  
**Last updated:** 2026-10-13  
**Baseline:** Phase 9 incident operational discovery signed off; Phases 1–9 capabilities in tree (including uncommitted Phase 9 filter work)

This document defines **organization-scoped on-call schedules** with **computed rotations** (no paging, notifications, or escalation execution). Implementation must follow existing Flyway, `OrganizationAuthorizationService`, Java record DTOs, `frontend/src/lib/api/*`, TanStack Query, and Playwright conventions.

---

## 1. Problem statement and goals

### Problem

Operators can assign an incident **commander** and filter incidents by commander, but the platform does not answer **who is on call right now** for a team or service, or **when the next handoff** occurs. That gap blocks realistic escalation and notification work in later phases.

### Goals (Phase 10)

- Let each organization define **named on-call schedules** with a **timezone-aware rotation** over **organization members**.
- Optionally scope a schedule to a **team** or **service** (at most one scope target) for operational clarity.
- Expose **read APIs** to resolve **current primary responder** and **upcoming handoffs** at an instant.
- Provide **CRUD UI** for authorized users, consistent with services/teams RBAC patterns.
- Lay a **stable API surface** for Phase 11 (escalation policies) and Phase 12 (notifications) without implementing them.

### Success criteria

- Any org member can list schedules and view coverage for schedules in their org.
- OWNER/ADMIN can create, update, archive, and manage rotation participants.
- Coverage calculation is deterministic, tested across timezones and DST boundaries.
- Cross-organization access returns **403** (existing `ForbiddenException` semantics).
- No new global roles; organization membership is required for every operation.

---

## 2. MVP scope and explicit exclusions

### In scope (Phase 10 MVP)

| Capability | MVP choice |
|------------|------------|
| Schedule ownership | Organization-scoped; optional `teamId` **or** `serviceId` (mutually exclusive) |
| Rotation model | **Fixed-interval** rotation: `rotationLengthDays` of **1** (daily) or **7** (weekly) |
| Handoff boundary | Local **time** + (for weekly) **day-of-week** in schedule `timezone` (IANA) |
| Participants | **Primary ring only** — ordered list of org members |
| Lifecycle | `ACTIVE` / `ARCHIVED`; archive instead of deleting active schedules |
| Coverage API | Current primary responder + next *N* handoffs (computed, not stored per shift) |
| Permissions | Read: all members; write: OWNER/ADMIN (`canManageOrganization`) |
| Audit | `created_at`, `updated_at`, `created_by_id`, `updated_by_id` on schedule row |

### Explicitly deferred (not Phase 10)

| Capability | Target phase / note |
|------------|-------------------|
| **Secondary / backup** on-call layer | Phase 11 — model as escalation step or second schedule |
| **Paging, SMS, email, Slack** | Phase 12 notifications |
| **Escalation policy execution** | Phase 11 |
| **Calendar sync** (Google/Outlook), PagerDuty/Opsgenie import | Phase 16+ integrations |
| **Per-shift overrides**, swaps, PTO, one-off coverage | Phase 10b or 11 (materialized `schedule_overrides` table) |
| **iCal RRULE / generic recurrence engine** | Not required for daily/weekly MVP |
| **Auto-set incident `commander_id`** from on-call | Future convenience; incidents unchanged in Phase 10 |
| **Org-wide audit log product** | Phase 15; no `IncidentEvent` types for schedules |
| **Redis / cached coverage** | Phase 17; compute on read is sufficient initially |
| **Notifications when handoff occurs** | Phase 12 |

### Future integration points (design hooks only)

- **Escalation policies (Phase 11):** FK `escalation_policy.schedule_id` → on-call schedule; first step = current primary user.
- **Notifications (Phase 12):** resolve `userId` from coverage API at incident create/severity change.
- **Incidents:** optional UI hint “Suggested commander” from service’s linked schedule — frontend-only read of coverage API; no schema change.
- **Dashboard (Phase 14):** widget “Who is on call” per org or service.

---

## 3. User journeys and acceptance criteria

### Journey A — Admin creates a weekly rotation for a service

1. OWNER opens **On-call** under the organization.
2. Creates schedule “Payments primary”, timezone `America/New_York`, weekly handoff Monday 09:00, links **service** Payments.
3. Adds three org members in rotation order.
4. Saves as **ACTIVE**.
5. Detail view shows **current** responder and **next two handoffs** with UTC instants and local labels.

**Acceptance:** `GET coverage` at a known instant returns the expected user index; participant order changes after handoff boundary.

### Journey B — Member views who is on call

1. MEMBER opens org on-call list (read-only controls).
2. Opens a schedule; sees current primary and upcoming handoffs.
3. Cannot edit participants or archive (buttons hidden / API 403).

**Acceptance:** UI matches `canManageOrganization`; API enforces same rules.

### Journey C — Team-scoped schedule

1. ADMIN creates schedule scoped to **team** Platform, participants must be **team members** (validation).
2. Team detail or on-call detail shows team name and link.

**Acceptance:** Adding a user who is org member but not on the team → **409** `VALIDATION_ERROR`.

### Journey D — Archive schedule

1. ADMIN archives an active schedule.
2. List defaults to active schedules; archived visible via filter or “Show archived”.
3. Coverage API for archived schedule → **409** `SCHEDULE_NOT_ACTIVE` (or 404 — pick one and document; **recommend 409** with stable code).

**Acceptance:** Archived schedules are not used for “suggested commander” in future UI without explicit reactivation.

### Journey E — Invalid configuration blocked

1. ADMIN attempts ACTIVE with zero participants → **409**.
2. ADMIN sets both `teamId` and `serviceId` → **409**.
3. Non-member user id in participant list → **409**.

---

## 4. Domain model and relationships

### 4.1 Entities

```text
Organization 1──* OnCallSchedule 1──* OnCallScheduleParticipant *──1 User
                      │ optional
                      ├── Team (same org)
                      └── Service (same org)
```

### 4.2 `OnCallSchedule`

| Field | Type | Notes |
|-------|------|--------|
| `id` | UUID | PK |
| `organization_id` | UUID | FK `organizations`, CASCADE |
| `name` | VARCHAR(100) | Unique per org (case-insensitive in app logic, like teams) |
| `status` | VARCHAR(20) | `ACTIVE`, `ARCHIVED` |
| `timezone` | VARCHAR(64) | IANA ID, e.g. `Europe/London` |
| `rotation_length_days` | SMALLINT | `1` or `7` only in MVP |
| `handoff_local_time` | TIME | Wall-clock handoff in `timezone` |
| `handoff_day_of_week` | SMALLINT | ISO-8601 Monday=1 … Sunday=7; **required** when `rotation_length_days = 7`; NULL when daily |
| `effective_from` | DATE | First calendar day rotation may apply; first handoff computed in TZ |
| `team_id` | UUID | Nullable; FK `teams`; must match org |
| `service_id` | UUID | Nullable; FK `services`; must match org |
| `created_by_id`, `updated_by_id` | UUID | FK `users` RESTRICT |
| `created_at`, `updated_at` | TIMESTAMPTZ | UTC |

**Constraints:**

- `CHECK (status IN ('ACTIVE','ARCHIVED'))`
- `CHECK (rotation_length_days IN (1, 7))`
- `CHECK (team_id IS NULL OR service_id IS NULL)` — not both scoped
- `UNIQUE (organization_id, lower(name))` via application or functional index

**Indexes:** `(organization_id)`, `(organization_id, status)`, `(organization_id, team_id)`, `(organization_id, service_id)`

### 4.3 `OnCallScheduleParticipant`

| Field | Type | Notes |
|-------|------|--------|
| `id` | UUID | PK |
| `schedule_id` | UUID | FK `on_call_schedules` CASCADE |
| `user_id` | UUID | FK `users` RESTRICT |
| `position` | INT | 0-based order in rotation |
| `created_at` | TIMESTAMPTZ | |

**Constraints:**

- `UNIQUE (schedule_id, user_id)`
- `UNIQUE (schedule_id, position)`
- `CHECK (position >= 0)`

Participants are replaced **wholesale** on update (delete + insert in transaction) or diffed in service layer — implementation choice; API exposes ordered `userIds[]`.

### 4.4 Ownership and isolation

- Every row carries implicit org scope via `schedule.organization_id`.
- All reads/writes validate `organizationId` path param + `requireMembership`.
- `team_id` / `service_id` validated with `requireTeamInOrganization` / `requireServiceInOrganization`.
- **Global `User.role` does not grant org access.**

### 4.5 Lifecycle

| Action | Behavior |
|--------|----------|
| Create | Default `ACTIVE` if participants non-empty; else `DRAFT` optional — **MVP: require ≥1 participant to create ACTIVE**; empty create rejected |
| Update metadata / participants | OWNER/ADMIN; bumps `updated_at` |
| Archive | Sets `ARCHIVED`; coverage queries fail with not-active |
| Reactivate | OWNER/ADMIN; `ARCHIVED` → `ACTIVE` if participants valid |
| Hard delete | **MVP: disallow** if ever `ACTIVE`; optional delete draft-only with no history — **recommend archive-only** for simplicity |

### 4.6 Deletion and references

- No FK from `incidents` in Phase 10.
- Team delete: `ON DELETE RESTRICT` on `team_id` if schedule references team (admin must reassign or archive first).
- Service delete: same **RESTRICT** if referenced.
- User removed from org: **RESTRICT** if still listed as participant — admin must remove from rotation first (or cascade job in membership removal — **defer**; document as ops step in MVP).

---

## 5. Rotation calculation (no recurrence engine)

### 5.1 Approach

Store **rotation parameters** and **ordered participants**; compute shifts at query time using `java.time` (`ZoneId`, `ZonedDateTime`, `Duration`). This avoids materializing years of shifts and avoids RRULE complexity for MVP.

### 5.2 Handoff instants

1. **Anchor (first handoff):**
   - **Daily (`rotation_length_days = 1`):** `effective_from` at `handoff_local_time` in the schedule `timezone`.
   - **Weekly (`rotation_length_days = 7`):** the first calendar date **on or after** `effective_from` whose ISO day-of-week equals `handoff_day_of_week` (Monday = 1 … Sunday = 7), at `handoff_local_time` in the schedule `timezone`.
2. If the query instant is **strictly before** the anchor, `current` is **null**; `nextHandoffAt` is the anchor instant; `upcoming` lists shifts from the anchor forward.
3. At the handoff instant **inclusive**, the incoming participant is current (`[handoff, nextHandoff)` windows).
4. Each shift length = `rotation_length_days` calendar days in the zone (`ZonedDateTime.plusDays`, not fixed UTC hour multiples).
5. Shift index `k` = count of completed handoffs since the anchor for the query instant.
6. Primary user = `participants[k % n]` (ordered by `position`).

**DST policy (`OnCallRotationCalculator`):** handoffs are built with `ZonedDateTime.of(localDate, handoffLocalTime, zone)`. Nonexistent local times in a spring-forward gap shift **forward**; ambiguous overlap times use the **earlier** offset (Java default).

### 5.3 Weekly vs daily

- **Daily:** `handoff_day_of_week` ignored; every day at `handoff_local_time`.
- **Weekly:** handoff once per week at configured DOW + time.

### 5.4 DST and ambiguity

- All handoffs computed in `ZoneId` rules (gap/overlap handled by `java.time`; **do not** use manual offset math).
- **Tests required** for `America/New_York` spring forward / fall back weeks.
- API represents instants as **ISO-8601 UTC** (`OffsetDateTime` / `Instant` in JSON) plus echo `timezone` on schedule/coverage responses.

### 5.5 Edge cases

| Case | Behavior |
|------|----------|
| Zero participants | Cannot activate; coverage → **409** `SCHEDULE_EMPTY` |
| One participant | Always on call when active and after anchor |
| Overlapping schedules (same service) | **Allowed** in MVP; UI may show multiple schedules; product doc warns operators |
| Query `at` in past/future | Supported for debugging; same formula |
| Archived schedule | Coverage → **409** `SCHEDULE_NOT_ACTIVE` |
| Invalid timezone string | Create/update → **409** `VALIDATION_ERROR` |

### 5.6 Trade-off summary

| Option | Pros | Cons |
|--------|------|------|
| **Computed rotation (chosen)** | Small schema; easy daily/weekly; testable pure functions | Overrides/swaps need later table |
| Materialized shift rows | Easy overrides | Migration churn; many rows; sync on edit |
| RRULE engine | Flexible | Overkill; hard to test; scope creep |

---

## 6. Database schema and migration strategy

### 6.1 Flyway

- **V10__on_call_schedules.sql** (next version after V9).
- Schema `app`, UUID PKs, `TIMESTAMPTZ`, consistent with V4–V9.

### 6.2 Tables

- `app.on_call_schedules` — fields in §4.2
- `app.on_call_schedule_participants` — fields in §4.3

### 6.3 Migration notes

- No changes to `incidents`, `services`, `teams` beyond optional future nullable `default_on_call_schedule_id` — **not in Phase 10**.
- Update [database.md](database.md) when implemented.

---

## 7. API contracts

Base path: `/api/v1/organizations/{organizationId}/on-call-schedules`  
Authentication: Bearer JWT (existing).  
Errors: `ApiErrorResponse` with stable `error` codes; **403** non-member; **404** unknown schedule id in org (optional — may use 403 only per existing team/service style).

### 7.1 List schedules

`GET /api/v1/organizations/{organizationId}/on-call-schedules`

| | |
|--|--|
| Permission | Any org member |
| Query | `status` optional: `ACTIVE`, `ARCHIVED`, `ALL` (default `ACTIVE`) |
| Response | `OnCallScheduleSummary[]` — id, name, status, timezone, rotationLengthDays, teamId?, serviceId?, participantCount, optional embedded `currentPrimaryUserId` if cheap (or omit for list performance) |

### 7.2 Create schedule

`POST /api/v1/organizations/{organizationId}/on-call-schedules`

| | |
|--|--|
| Permission | OWNER, ADMIN |
| Body | `CreateOnCallScheduleRequest` |
| Validation | Name, timezone, rotation params, optional teamId XOR serviceId, `participantUserIds` non-empty ordered list |
| Response | **201** `OnCallScheduleResponse` |

### 7.3 Get schedule

`GET /api/v1/organizations/{organizationId}/on-call-schedules/{scheduleId}`

| | |
|--|--|
| Permission | Any org member |
| Response | Full schedule + ordered participants (user id, display name) |

### 7.4 Update schedule

`PATCH /api/v1/organizations/{organizationId}/on-call-schedules/{scheduleId}`

| | |
|--|--|
| Permission | OWNER, ADMIN |
| Body | `UpdateOnCallScheduleRequest` — partial fields; `participantUserIds` replaces order when present |
| Response | `OnCallScheduleResponse` |

### 7.5 Archive / reactivate

`POST .../on-call-schedules/{scheduleId}/archive`  
`POST .../on-call-schedules/{scheduleId}/reactivate`

| | |
|--|--|
| Permission | OWNER, ADMIN |
| Response | Updated schedule |

### 7.6 Coverage (current + upcoming)

`GET /api/v1/organizations/{organizationId}/on-call-schedules/{scheduleId}/coverage`

| | |
|--|--|
| Permission | Any org member |
| Query | `at` optional ISO-8601 instant (default `now`); `upcoming` int default `3`, max `20` |
| Response | `OnCallCoverageResponse` |

**Response shape (illustrative):**

```json
{
  "scheduleId": "uuid",
  "status": "ACTIVE",
  "timezone": "America/New_York",
  "queriedAt": "2026-10-13T18:00:00Z",
  "current": {
    "userId": "uuid",
    "displayName": "Jane Doe",
    "shiftStartsAt": "2026-10-13T13:00:00Z",
    "shiftEndsAt": "2026-10-20T13:00:00Z"
  },
  "upcoming": [
    {
      "userId": "uuid",
      "displayName": "Bob Smith",
      "shiftStartsAt": "2026-10-20T13:00:00Z",
      "shiftEndsAt": "2026-10-27T13:00:00Z"
    }
  ]
}
```

If before `effective_from` anchor: `current: null`, `upcoming` lists future shifts.

**Calculation:** documented in §5; implemented in `OnCallRotationCalculator` (pure, unit-tested).

### 7.7 Resolve by scope (convenience)

`GET /api/v1/organizations/{organizationId}/on-call-schedules/coverage`

| | |
|--|--|
| Query | Exactly one of `serviceId`, `teamId`, or `scheduleId`; plus `at`, `upcoming` |
| Behavior | If `scheduleId`, same as §7.6. If service/team, find **single ACTIVE** schedule for that scope; if **0** → **404** `SCHEDULE_NOT_FOUND`; if **>1** → **409** `AMBIGUOUS_SCHEDULE` (forces explicit schedule id) |
| Rationale | Enables service detail widget without listing all schedules |

### 7.8 DTO validation summary

| Rule | HTTP | `error` |
|------|------|---------|
| Invalid timezone, DOW, rotation length | 409 | `VALIDATION_ERROR` |
| Both teamId and serviceId | 409 | `VALIDATION_ERROR` |
| Participant not org member | 409 | `VALIDATION_ERROR` |
| Participant not team member when team scoped | 409 | `VALIDATION_ERROR` |
| Duplicate name in org | 409 | `DUPLICATE_NAME` |
| Empty participants on create/activate | 409 | `SCHEDULE_EMPTY` |
| Coverage on archived | 409 | `SCHEDULE_NOT_ACTIVE` |
| Non-member | 403 | (existing) |

Document in [api.md](api.md) when implemented.

---

## 8. Authorization matrix

| Operation | OWNER | ADMIN | MEMBER | VIEWER |
|-----------|-------|-------|--------|--------|
| List / get schedule | ✓ | ✓ | ✓ | ✓ |
| Get coverage | ✓ | ✓ | ✓ | ✓ |
| Create / PATCH / archive / reactivate | ✓ | ✓ | — | — |
| Manage participants (via PATCH) | ✓ | ✓ | — | — |

Enforcement: `OrganizationAuthorizationService.requireMembership` + `requireRole(..., OWNER, ADMIN)` for mutations (same as [ServiceService](backend/src/main/java/com/example/incidentmanagement/service/ServiceService.java)).

Non-members and wrong-org IDs: **403** (no existence leak).

---

## 9. Frontend interaction design

### 9.1 Navigation

- Add **On-call** to `OrganizationNav` between **Services** and **Incidents** (or after Services).
- Routes:
  - `/organizations/[organizationId]/on-call` — list
  - `/organizations/[organizationId]/on-call/new` — create (gated)
  - `/organizations/[organizationId]/on-call/[scheduleId]` — detail + coverage

### 9.2 List page

- Table/cards: name, scope (org-wide / team / service), timezone, status, current primary (from coverage fetch per row or batch endpoint — **MVP: fetch coverage on detail only**; list shows participant count).
- Filter: Active / Archived tabs (URL `?status=archived` optional).
- **Create schedule** button if `canManageOrganization`.
- Empty state: explain on-call + link to docs; CTA create for admins.

### 9.3 Create / edit form

- Fields mirror API; timezone select (curated IANA list or searchable).
- Rotation: Daily vs Weekly (maps to `rotationLengthDays` 1/7).
- Participant picker: org members (and filter to team members when team scoped) — reuse patterns from incident commander member list.
- Reorder participants (simple up/down buttons).
- Save / Cancel; archive on detail for admins.

### 9.4 Detail page

- **Current on call** card: name, shift start/end (show local + UTC tooltip).
- **Upcoming handoffs** list.
- Read-only for MEMBER/VIEWER.

### 9.5 States

| State | UX |
|-------|-----|
| Loading | Skeleton / spinner via TanStack Query `isPending` |
| Error | `ApiClientError` message + retry |
| Permission denied | Hide actions; 403 on mutate shows toast |
| Empty list | Zero schedules copy |
| Before effective_from | “Rotation starts on …” |

### 9.6 Data layer

- `frontend/src/lib/api/on-call-schedules.ts` — CRUD + coverage helpers.
- `useOrganizationOnCallSchedules`, `useOnCallSchedule`, `useOnCallCoverage` hooks.
- Query keys: `['organizations', orgId, 'on-call-schedules', ...]`.
- Invalidate coverage on schedule PATCH.

No new global state library.

### 9.7 Optional service detail hook (Phase 10 if time permits)

- On service detail, if exactly one ACTIVE schedule for `serviceId`, show “On call now: …” linking to schedule — **nice-to-have** in Step 7; not required for sign-off if list + detail ship.

---

## 10. Security and reliability

| Concern | Mitigation |
|---------|------------|
| Cross-org access | All queries filter by `organization_id`; integration tests mirror incident/service tests |
| Participant validation | Must be org member; team-scoped schedules require team membership |
| Privilege escalation | VIEWER cannot PATCH; no use of global ADMIN bypass for org routes |
| Concurrent edits | Last-write-wins on PATCH; optional `@Version` if flakiness observed — **start without** |
| DST correctness | `ZoneId` + dedicated unit tests |
| Ambiguous service coverage | `AMBIGUOUS_SCHEDULE` forces explicit schedule selection |
| Archived schedules | No coverage for paging hooks until reactivated |
| Rate of coverage calls | Acceptable for MVP; cache later in Phase 17 |

---

## 11. Testing strategy

### 11.1 Backend unit tests

- `OnCallRotationCalculatorTest` — daily/weekly, index wrap, anchor before now, DST weeks (America/New_York, Europe/London).
- `OnCallScheduleServiceTest` — validation rules, participant membership, XOR scope.

### 11.2 Backend integration tests (Testcontainers)

- Extend `IntegrationTestBase` + PostgreSQL container (existing `SharedPostgresContainer`).
- `OnCallScheduleControllerIntegrationTest` — CRUD, 403 cross-org, archive blocks coverage, ambiguous scope query.
- Repository: unique name, participant order, FK RESTRICT on team/service delete.

### 11.3 Frontend unit tests (Vitest)

- Pure helpers: map API DTO → display labels; timezone formatting wrapper if added.
- Optional: rotation display helper if any client-side preview is added (prefer server coverage as source of truth).

### 11.4 Playwright E2E

- `tests/on-call.spec.ts`:
  - Admin creates weekly schedule with 2 participants; assert coverage UI shows one name.
  - Member read-only cannot see create button.
  - Archive → coverage error or empty state on detail.

**Prerequisites:** Docker Postgres; backend with V10 migration; same auth helpers as existing specs.

---

## 12. Implementation sequence

| Step | Objective | Deliverables | Tests |
|------|-----------|--------------|-------|
| **0** | Design sign-off | This document + development-plan update | Review |
| **1** | Migration + entities | V10, JPA entities, repositories | Persistence / FK tests |
| **2** | Rotation calculator | `OnCallRotationCalculator` | Unit tests (incl. DST) |
| **3** | Service layer | `OnCallScheduleService`, validation | Service unit + integration |
| **4** | REST API + api.md | Controller, DTOs | Controller integration tests |
| **5** | Frontend API + types | `lib/api/on-call-schedules.ts`, types | Vitest if helpers |
| **6** | List + detail UI | Nav, pages, coverage display | Manual + lint/typecheck |
| **7** | Create/edit UI | Forms, participant order | Manual |
| **8** | Playwright | `on-call.spec.ts` | E2E |
| **9** | Sign-off | development-plan, database.md | Full `./gradlew test`, frontend checks, Playwright |

**Completion gate:** Coverage API matches calculator tests; RBAC matches matrix; E2E green with `--workers=1` against shared backend.

---

## 13. Risks, trade-offs, and open decisions

| Risk | Mitigation |
|------|------------|
| Scope creep into notifications | Strict defer list in §2 |
| DST bugs in production | Mandatory zone tests; never hand-roll offsets |
| Multiple schedules per service | Ambiguity error on convenience endpoint |
| User leaves org while on rotation | Document manual removal; later hook membership removal |
| Effort underestimated (roadmap XL) | Fixed daily/weekly only; no overrides |

### Resolved decisions (approved for implementation)

1. **Team-scoped participants** must belong to the scoped team (enforced in service layer; not a DB FK).
2. **Coverage before `effective_from`:** `current: null` until the first handoff anchor.
3. **List endpoint** does not embed computed current-primary coverage.
4. **Lifecycle:** archive-only (`ARCHIVED` status); no hard-delete product path.

---

## 14. Reusable platform capabilities (baseline)

| Area | Reuse |
|------|--------|
| Tenancy | `OrganizationAuthorizationService`, org-prefixed routes |
| Teams / services | Optional FKs; existing validators |
| Users | Participant picker like incident commander |
| API style | `/api/v1/organizations/{id}/…`, Java records, 409 validation |
| Frontend | `OrganizationNav`, `canManageOrganization`, TanStack Query, Playwright `e2e-api` helpers |
| Tests | `IntegrationTestBase`, Testcontainers PostgreSQL |
| Incidents | `commander_id` remains manual; discovery filters (`mine`, commander) complement on-call visibility |
