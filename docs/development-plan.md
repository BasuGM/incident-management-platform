# Development plan

## Phase 1: Foundation

Project structure, Docker Compose (PostgreSQL, Redis), Spring Boot and Next.js skeletons, API/error conventions, testing harness, and documentation.

**Status:** completed

## Phase 2: Authentication and authorization

User identity, sessions or tokens, roles, and API protection.

**Status:** completed (register/login/refresh/logout, users API, RBAC, frontend auth shell, tests)

## Phase 3: Organizations and teams

Tenancy, teams, membership, and basic admin flows.

**Status:** completed (organizations, organization memberships, teams, team memberships, tenant isolation, frontend org selector)

## Phase 4: Service catalog and organization UX

Service catalog (backend and frontend), organization landing experience, membership management UI, and RBAC-aware navigation.

**Status:** completed

### Completed scope

- **Service catalog (backend)** — organization-scoped services REST API, persistence, authorization, and tests.
- **Service catalog (frontend)** — list, create, update, delete, and detail flows under `/organizations/{id}/services`.
- **Organization dashboard / landing UX** — `/dashboard` as the authenticated home: welcome, org cards with role, open actions, and zero-org empty state.
- **Organization overview navigation** — `/organizations/{id}` as a hub with links to Teams, Services, Members, and Settings (when permitted).
- **Organization membership UI** — `/organizations/{id}/members` using existing APIs (list, add by user ID, change role, remove); no email invitation flow.
- **Organization settings UI** — `/organizations/{id}/settings` for name/slug updates (OWNER/ADMIN).
- **Organization selector behavior** — lists only the user’s organizations; switching updates context and navigates when already on an org route.
- **RBAC-aware UI** — management controls gated with `canManageOrganization` (OWNER/ADMIN); MEMBER/VIEWER read-only where appropriate.
- **Zero-organization empty state** — shared empty state on dashboard and organization list with create-org guidance (no arbitrary “join org” flow).

### Verified (frontend, organization UX completion)

- `npm run lint` — PASS
- `npm run typecheck` — PASS
- `npm run build` — PASS
- `NEXT_PUBLIC_API_URL=http://localhost:8080 npm run test:e2e` — **15/15 PASS**

Backend was unchanged during the organization UX work; backend test runs were not part of that verification pass.

## Phase 5: Incidents

Incident creation, status, severity, and core fields.

**Status:** in progress — Steps 1–4 (backend + frontend incident UI) complete; see [Phase 5 incident design](phase-5-incident-design.md)

## Phase 6: Incident timeline / comments / assignment

Immutable incident audit events and a read-only timeline for incident history (Phase 6 scope). **Deferred from Phase 6:** incident comments, @mentions, assignment workflows beyond existing commander field, notifications, on-call/escalation, postmortems, and deployment correlation.

**Status:** completed

### Phase 6 steps

| Step | Scope | Status |
|------|--------|--------|
| 1 | `app.incident_events`, domain model, repository, persistence tests | complete |
| 2 | Transactional event generation from `IncidentService` via `IncidentEventRecorder` | complete |
| 3 | Read-only paginated timeline REST API | complete |
| 4 | Frontend incident detail timeline UI | complete |
| 5 | Audit, verification, sign-off | complete |

### Delivered (Phase 6)

- Append-only incident events (`INCIDENT_CREATED`, field/status changes) with JSONB payloads
- Same-transaction incident mutation + event recording (rollback-tested)
- `GET .../incidents/{incidentId}/events` for organization members (tenant-scoped)
- Incident detail timeline: human-readable events, actor display, load-more pagination, loading/empty/error states
- Automated coverage: backend integration/unit tests; Playwright incident timeline flows

## Phase 7: Incident comments & collaboration

Organization-scoped plain-text discussion on incidents, separate from the Phase 6 immutable audit timeline.

**Status:** completed — see [Phase 7 comments design](phase-7-comments-design.md)

**Deferred from Phase 7:** @mentions, attachments, Markdown, notifications, and timeline/audit integration for comments.

| Step | Scope | Status |
|------|--------|--------|
| 1 | `app.incident_comments`, `IncidentComment`, repository, persistence tests | complete |
| 2 | Authorization + service layer | complete |
| 3 | REST API + DTOs + controller integration tests + API docs | complete |
| 4 | Backend comment API audit & hardening | complete |
| 5 | Frontend API/types | complete |
| 6 | Comment UI | complete |
| 7 | Playwright E2E | complete |
| 8 | Final audit, documentation & sign-off | complete |
| 9 | Phase 7 sign-off | complete (Step 8) |

### Delivered (Phase 7)

- `app.incident_comments` with soft delete (tombstones in list responses), chronological pagination, and terminal-incident write lock
- Tenant-scoped REST API: `GET` / `POST` / `PATCH` / `DELETE` on `.../incidents/{incidentId}/comments`
- Organization-role RBAC (VIEWER read-only; author-only edit; OWNER/ADMIN moderation via delete only); no `IncidentEvent` coupling
- Incident detail Comments section (plain text, load-more, composer/edit/delete, tombstones) before Timeline
- Automated coverage: backend integration/unit tests; Playwright incident comment flows

### Verified (Phase 7 completion)

- `./gradlew clean test` — **177/177 PASS**
- `npm run lint` — PASS
- `npm run typecheck` — PASS
- `npm run build` — PASS
- `npx playwright test` — **30/30 PASS**

## Phase 8: Postmortems

Structured follow-up and learning loops after incidents are resolved.

**Status:** **complete** — see [Phase 8 postmortems design](phase-8-postmortems-design.md)

**Deferred from Phase 8:** structured action items (assignee, status, due dates), formal review/approval workflow, Markdown/rich text, multiple published versions per incident, new `IncidentEvent` types, notifications.

| Step | Scope | Status |
|------|--------|--------|
| 0 | Domain, API, schema, UX, and test design | complete |
| 1 | `app.incident_postmortems`, entity, repository, persistence tests | complete |
| 2 | `IncidentPostmortemService` — eligibility, RBAC, lifecycle | complete |
| 3 | REST API + DTOs + controller integration tests | complete |
| 4 | Backend audit; contract hardening and regression tests | complete |
| 5 | Frontend types and API layer | complete |
| 6 | Incident detail postmortem UI | complete |
| 7 | Organization postmortems list + navigation | complete |
| 8 | Playwright E2E | complete |
| 9 | Final audit, documentation sign-off | complete |

### Delivered (Phase 8)

- `V9__incident_postmortems.sql`, `IncidentPostmortem` entity/repository/service, REST controllers and DTOs
- Frontend types, API client, TanStack Query hooks, incident postmortem UI, organization library page and nav
- Vitest unit tests; Playwright postmortem lifecycle, library, and RBAC flows

### Verified (Phase 8 Step 9 — 2026-10-09)

Re-run during final audit (Docker Postgres/Redis healthy; backend on port 8080 with postmortem routes):

- `backend/` `./gradlew clean test` — **239/239 PASS**
- `frontend/` `npm run test:unit` — **44/44 PASS**
- `frontend/` `npx playwright test tests/postmortems-library.spec.ts tests/postmortems.spec.ts` — **5/5 PASS**
- `frontend/` `npx playwright test` — **35/35 PASS**
- `frontend/` `npm run lint`, `npm run typecheck`, `npm run build` — **PASS**

**Accepted test limitations:** no RTL component tests; `POSTMORTEM_NOT_FOUND` vs unrelated 404/403 covered in Vitest only; org list omits incident display metadata; unarchive returns `DRAFT` while retaining `publishedAt` / publisher fields (documented).

## Phase 9: Incident operational discovery

List filtering, active-incident views, and search to complete deferred Phase 5 discoverability and support commander workflows.

**Status:** **signed off** (2026-10-13) — design: [phase-9-incident-discovery-design.md](phase-9-incident-discovery-design.md), roadmap: [phase-9-roadmap.md](phase-9-roadmap.md)

**Note:** On-call schedules remain a planned follow-on (formerly listed here); sequencing rationale is in the roadmap.

### Phase 9 steps

| Step | Scope | Status |
|------|--------|--------|
| 0 | Gap analysis, ranked initiatives, roadmap | complete |
| 1 | Design doc (`phase-9-incident-discovery-design.md`) | complete |
| 2 | Backend incident list filters + tests | complete |
| 3 | REST API + `api.md` | complete |
| 4 | Frontend filters + URL state | complete |
| 5 | Playwright + sign-off | complete |

### Phase 9 Step 5 — verification (2026-10-13)

**Login / E2E environment (diagnosis):** Earlier failures to leave `/login` were not a core auth defect. Causes included a stale backend JVM without Phase 9 list filters, Playwright `webServer` not inheriting `NEXT_PUBLIC_API_URL` (fixed in `playwright.config.ts`), flaky `loginThroughUi` timing (fixed in `tests/helpers/e2e-api.ts`), and missing Playwright browser binaries on a fresh agent host (`npx playwright install chromium`).

**Commands and results:**

| Command | Result |
|---------|--------|
| `backend/` `./gradlew test` | **PASS** (BUILD SUCCESSFUL) |
| `frontend/` `npm run test:unit` | **52/52 PASS** |
| `frontend/` `npm run lint` | **PASS** |
| `frontend/` `npm run typecheck` | **PASS** |
| `frontend/` `npm run build` | **PASS** |
| `frontend/` `npx playwright test tests/incidents-discovery.spec.ts` | **1/1 PASS** |
| `frontend/` `npx playwright test --workers=1` | **36/36 PASS** |

**E2E prerequisites:** Docker Postgres/Redis; backend on `:8080` from current tree with repo root `.env`; frontend via Playwright `webServer` or existing dev server on `:3000`. Restart backend after pulling filter changes so list `active=true` is enforced server-side.

**Test fix (Step 5):** `tests/incidents.spec.ts` — severity filter on the list page made `getByText('SEV2')` ambiguous; assert on table cell instead.

**Accepted limitations:** Local full-suite E2E should use `--workers=1` (or `CI=true`) when sharing one backend; parallel workers previously caused flaky failures unrelated to discovery. Discovery coverage is consolidated in one Playwright test (active default, all view, severity, mine, search by title/number, pagination + reload, empty state clear filters).

Parallel engineering (recommended): CI workflow, README and stale doc refresh (see roadmap §7).

## Phase 10: On-call schedules

Organization-scoped rotation definitions, primary on-call coverage (computed daily/weekly handoffs), and read APIs for current/upcoming responders. Builds on org membership, teams, services, and existing RBAC.

**Status:** **signed off** (2026-10-13) — design: [phase-10-on-call-design.md](phase-10-on-call-design.md). Automated verification complete; **not deployed to production** as part of this sign-off.

**Prerequisites delivered:** Phase 9 discovery (commander/`mine` views); Phases 3–4 teams/services; no notification or escalation infrastructure required for Phase 10 MVP.

**Explicitly not in Phase 10:** paging, email/SMS/Slack, escalation execution, calendar sync, external on-call vendors, per-shift overrides, secondary on-call layer, auto-assign incident commander, materialized recurrence/RRULE engine.

### Phase 10 steps (proposed)

| Step | Scope | Status |
|------|--------|--------|
| 0 | Design doc + plan alignment | complete |
| 1 | `V10` migration (`on_call_schedules`, `on_call_schedule_participants`) | complete |
| 2 | Entities, repositories | complete |
| 3 | Rotation calculator (timezone/DST) | complete |
| 4 | `OnCallScheduleService` + validation | complete |
| 5 | REST API + `api.md` | complete |
| 6 | Frontend API types + hooks | complete |
| 7 | List + detail + coverage UI | complete |
| 8 | Create/edit + participant ordering | complete |
| 9 | E2E + regression audit | complete |
| 10 | Verification sign-off | complete |

**Open product decisions (pre-implementation):** team-scoped participant membership rule; behavior before `effective_from` anchor; archive-only vs hard delete — resolved in design doc §13 and reflected in implementation.

### Phase 10 Step 9 — audit and verification (2026-10-13)

**Environment:** Docker Postgres/Redis healthy; backend on port **8080** with Phase 10 routes (`GET .../on-call-schedules` → **200** authenticated). Frontend Playwright `webServer` with `NEXT_PUBLIC_API_URL=http://localhost:8080`, `--workers=1`, `--retries=0`.

| Area | Result |
|------|--------|
| Backend `./gradlew test --tests 'com.example.incidentmanagement.oncall.*'` | **PASS** (46 on-call tests) |
| Backend `./gradlew test` | **PASS** (**302/302**, 0 skipped) |
| Frontend focused on-call Vitest (5 files) | **36/36 PASS** |
| Frontend `npm run test:unit` | **88/88 PASS** |
| Frontend `npm run lint`, `typecheck`, `build` | **PASS** |
| Playwright `on-call-schedules.spec.ts`, `on-call-schedules-management.spec.ts` | **2/2 PASS** |
| Playwright full suite | **38/38 PASS** |

**Audit summary:** MVP persistence, rotation calculator (handoff boundary, weekly anchor, DST gap/overlap, pre-`effective_from` null current), service validation, REST contract, frontend list/detail/coverage/forms, RBAC, archive/reactivate, and TanStack Query cache keys align with design and `docs/api.md`. Deferred scope-based coverage resolver and notification/escalation hooks remain unimplemented by design.

**Accepted limitations:** DB unique name is case-sensitive on `(organization_id, name)`; case-insensitive duplicate rejection is application-layer (`DUPLICATE_NAME`). No dedicated backend integration test for `clearScope` PATCH (covered by frontend form tests + E2E team-scope edit). No RTL component tests. Mid-session permission downgrade (403) covered in form handlers, not E2E. Concurrent duplicate-name races rely on app check + DB constraint, not load testing.

### Phase 10 Step 10 — final sign-off (2026-10-13)

**Sign-off owner:** Not recorded.

**Verification evidence** (from Step 9 audit, re-confirmed for closure):

| Command / check | Result |
|-----------------|--------|
| `backend/` `./gradlew test --tests 'com.example.incidentmanagement.oncall.*'` | **46/46 PASS** |
| `backend/` `./gradlew test` | **302/302 PASS** (0 failures, 0 skipped) |
| `frontend/` focused on-call Vitest (5 files under `src/lib/**/on-call*`, hooks, API) | **36/36 PASS** |
| `frontend/` `npm run test:unit` | **88/88 PASS** |
| `frontend/` `npm run lint` | **PASS** |
| `frontend/` `npm run typecheck` | **PASS** |
| `frontend/` `npm run build` | **PASS** |
| `frontend/` `npx playwright test tests/on-call-schedules.spec.ts tests/on-call-schedules-management.spec.ts` | **2/2 PASS** |
| `frontend/` `npx playwright test --workers=1` | **38/38 PASS** (0 skipped) |

**Environment at verification:** Docker Postgres/Redis; backend from current tree on port **8080** with on-call routes available; Playwright `webServer` with `NEXT_PUBLIC_API_URL=http://localhost:8080`.

#### Readiness checklist (local / CI)

| Item | Requirement |
|------|-------------|
| Database | Apply Flyway **V10** (`on_call_schedules`, `on_call_schedule_participants`); run migration integration tests when changing schema |
| Configuration | Repo-root `.env` (or equivalent) for DB/Redis; frontend `NEXT_PUBLIC_API_URL` pointing at the API used for E2E |
| Backend | `./gradlew bootRun` (or CI image) from **current** source after V10 migration |
| Frontend | `npm run dev` or Playwright-managed `webServer` on `:3000` |
| Unit / integration | `./gradlew test`; `frontend/` `npm run test:unit`, `lint`, `typecheck`, `build` |
| E2E | `npx playwright install chromium` on new hosts; `npx playwright test --workers=1` against a backend that includes Phase 10 (stale JVMs without on-call routes cause skips/failures) |
| Production deploy | Separate release process — this sign-off covers **repository verification only**, not production rollout |

#### Accepted limitations and deferred work (not blockers)

- **Schedule names:** case-insensitive uniqueness enforced in application code (`DUPLICATE_NAME`); database `UNIQUE (organization_id, name)` is case-sensitive.
- **`clearScope` PATCH:** no dedicated backend integration test (frontend form tests + E2E scope edit).
- **Scope-based coverage resolver** (`GET .../on-call-schedules/coverage` by team/service): deferred — see `api.md`.
- **Notifications and escalation execution:** Phase 12 / Phase 11.
- **Auto incident commander from on-call; real-time coverage (polling/WebSockets):** not implemented.

## Phase 11: Escalation policies

Automated escalation paths.

## Phase 12: Notifications

Email, chat, and push channels.

## Phase 13: Deployments

Change events correlated with incidents.

## Phase 14: Dashboard / metrics

Operational visibility and reporting.

## Phase 15: Audit logs

Immutable activity history.

## Phase 16: Webhooks / integrations

External monitoring and tooling.

## Phase 17: Redis / WebSockets / advanced infrastructure

Caching, rate limits, real-time updates, and async processing.

## Phase 18: Production hardening

Security review, observability, performance, and deployment automation.
