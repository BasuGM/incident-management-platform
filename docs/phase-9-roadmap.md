# Phase 9 — Product gap analysis and roadmap

**Status:** planning (Step 0)  
**Last updated:** 2026-10-13  
**Baseline:** `main` @ `1eb88a9` — Phase 8 postmortems complete

This document records a repository-verified assessment of the Developer Incident Management Platform and proposes a prioritized Phase 9 implementation sequence. It does not change production code.

---

## 1. Executive summary

The platform delivers a credible **incident response and learning loop** for a single organization tenant: auth and org RBAC, service catalog, incidents with lifecycle and commander, immutable timeline events, comments, and postmortems with an org library. Backend integration tests (Testcontainers + PostgreSQL) and Playwright E2E cover the main flows.

The largest **daily-operations gap** relative to implemented depth is **incident discoverability**: the incident list is paginated by `createdAt` only, while Phase 5 design specified filters (`active`, `status`, `severity`, `serviceId`) that were never shipped. Commander assignment exists on create/detail but there is no “my incidents” or filter-by-commander view.

**Recommended first Phase 9 initiative:** **Incident operational discovery** — list filters, an `active` default, optional text search, and commander-scoped views — as one coherent increment that completes deferred Phase 5 scope without new infrastructure.

**Parallel engineering priority (not a substitute for product work, but should run early):** add **CI** (no `.github/workflows` in repo), refresh **README** and stale design-doc headers, and align **development-plan** Phase 5 status with code.

The original [development plan](development-plan.md) labels Phase 9 as on-call schedules. On-call remains a high-value **follow-on** initiative (Phase 9b or renumbered phase) after operators can find and triage incidents at scale; it depends on clearer ownership views and benefits from notification foundations in a later phase.

---

## 2. Verified current-state inventory

### 2.1 Repository baseline (2026-10-13)

| Check | Result |
|-------|--------|
| Branch | `main`, tracking `origin/main` |
| HEAD | `1eb88a9` — `Add Phase 8 incident postmortems end-to-end.` |
| Working tree | Clean (`git status -sb`: no local modifications) |

### 2.2 Backend modules (Java packages under `com.example.incidentmanagement`)

| Package | Responsibility |
|---------|----------------|
| `auth/` | Register, login, refresh, logout; refresh tokens in PostgreSQL |
| `user/` | Users API, global roles (`ADMIN`, `ENGINEER`, `VIEWER`) |
| `organization/` | Orgs, memberships, `OrganizationAuthorizationService` |
| `team/` | Teams and team membership |
| `service/` | Organization service catalog |
| `incident/` | Incidents, events, comments, postmortems |
| `health/` | `/api/v1/health` |
| `config/`, `security/`, `common/` | Security, CORS, Redis wiring, errors |

Flyway migrations **V1–V9** through `backend/src/main/resources/db/migration/` (see [database.md](database.md)).

### 2.3 Frontend structure

- App Router under `frontend/src/app/(app)/` and `(auth)/`
- API client: `frontend/src/lib/api/*`
- TanStack Query for server state; org context via `organization-provider`
- Incident UI: list, create, detail (status, commander, service, comments, timeline, postmortem)
- Org postmortem library: `/organizations/[organizationId]/postmortems`

### 2.4 Automated tests and tooling

| Layer | Evidence |
|-------|----------|
| Backend | `backend/src/test/java/**` — integration tests extend `IntegrationTestBase`; incident/postmortem/comment/event controller and service tests |
| Frontend unit | Vitest (`npm run test:unit`) — postmortem helpers and API client tests |
| Frontend E2E | Playwright — `frontend/tests/*.spec.ts` (auth, orgs, services, incidents, comments, postmortems); **requires live API** at `NEXT_PUBLIC_API_URL` (see `tests/helpers/e2e-api.ts`) |
| CI | **No** GitHub Actions (or other) workflow files in repository; Playwright config supports `CI` env (`playwright.config.ts`) but nothing runs it automatically |
| Local infra | `docker-compose.yml` — PostgreSQL 16, Redis 7 |

### 2.5 Documentation vs code drift (verified)

| Document | Claim | Code reality |
|----------|-------|--------------|
| [README.md](../README.md) | “Incidents and services are not implemented” | **Stale** — Phases 4–8 implemented |
| [development-plan.md](development-plan.md) Phase 5 | “in progress” | Incidents, timeline, comments, postmortems **shipped**; Phase 5 checklist item “pagination and core filters” **not** done for filters |
| [phase-5-incident-design.md](phase-5-incident-design.md) | “design only — not implemented” | **Stale** header; implementation exists |
| [phase-8-postmortems-design.md](phase-8-postmortems-design.md) §18 | “NOT STARTED” | **Stale** — V9 migration and full stack delivered |
| [phase-8-postmortems-design.md](phase-8-postmortems-design.md) §11.9 | Org list includes `incidentDisplayId`, `incidentTitle` | **Not in API** — accepted limitation in development-plan Phase 8 notes; list uses postmortem title + `incidentId` only |

### 2.6 Cross-cutting capabilities

| Capability | Status |
|------------|--------|
| Redis | Configured (`RedisConfig`, `application.yml`); **no** application usage beyond connection factory |
| Notifications / webhooks | **Absent** |
| Rate limiting | **Absent** |
| Org-wide audit log product (Phase 14) | **Absent**; incident **events** are per-incident only |
| Production deployment | **Not** in repo (local Compose + host-run apps only) |
| Email org invites | **Absent** — members added by user UUID ([development-plan](development-plan.md) Phase 4) |

---

## 3. Gap analysis by dimension

For each dimension: **status**, **evidence**, **gaps**, **impact**, **priority class** (blocker / high-value / optional).

### Core incident lifecycle and operational workflows

- **Status:** Strong MVP — `OPEN` → `ACKNOWLEDGED` → `RESOLVED` / `CANCELLED`; PATCH enforces transitions (`IncidentService` `ALLOWED_TRANSITIONS`).
- **Evidence:** `IncidentService.java`, `IncidentController.java`, `frontend/.../incidents/[incidentId]/page.tsx`, `tests/incidents.spec.ts`.
- **Gaps:** No reopen from `RESOLVED`; no SLA timers; no incident templates.
- **Impact:** Reopen limits post-resolve correction without data migration workarounds.
- **Priority:** Optional (explicit non-goal unless product requests).

### Incident ownership, assignment, and collaboration

- **Status:** Commander field + member picker on create/edit; reporter server-assigned; comments for collaboration.
- **Evidence:** `V6__incidents.sql` `commander_id`; create page loads `listOrganizationMembers`; `COMMANDER_CHANGED` events via `IncidentEventRecorder`.
- **Gaps:** No responder list, no “assign to me” action, no list filter by commander/reporter, no @mentions (deferred Phase 7).
- **Impact:** Hard to answer “what am I commanding?” across many incidents.
- **Priority:** High-value — address via discovery initiative (commander filter, “mine” preset).

### Incident timeline and auditability

- **Status:** Complete for state changes — append-only `incident_events`, transactional with mutations.
- **Evidence:** `V7__incident_events.sql`, `IncidentEventController`, `incident-timeline.tsx`, rollback integration test.
- **Gaps:** Comments and postmortems **do not** emit events (by design); no org-wide audit export.
- **Impact:** Compliance teams lack single export; acceptable for current MVP.
- **Priority:** Optional until Phase 14 audit logs.

### Comments and postmortem workflows

- **Status:** Phase 7 and 8 complete per tests and docs.
- **Evidence:** `IncidentCommentService`, `IncidentPostmortemService`, Playwright `incident-comments.spec.ts`, `postmortems.spec.ts`.
- **Gaps:** Structured postmortem action items; review/approval; rich text — all explicitly deferred.
- **Impact:** Follow-up tracking is manual (text in `correctiveActions`).
- **Priority:** High-value **after** discovery; medium effort.

### Organization, team, service, and tenant boundaries

- **Status:** Consistent `organizationId` on APIs; `OrganizationAuthorizationService`; cross-tenant **403**.
- **Evidence:** Integration tests across incident/comment/postmortem controllers.
- **Gaps:** No sub-orgs; no SCIM; member onboarding by UUID only.
- **Impact:** Onboarding friction for real teams.
- **Priority:** Optional (invites) or later enterprise phase.

### Search, filtering, sorting, and discoverability

- **Status:** **Partial** — postmortem org list has `status` filter; **incident list does not**.
- **Evidence:** `IncidentController.listIncidents` — only `page`/`size`, sort `createdAt` DESC (`IncidentRepository.findByOrganizationId`); indexes exist for status/severity/service/commander (`V6__incidents.sql`); Phase 5 design §472 “Minimum useful filters”.
- **Gaps:** `active`, `status`, `severity`, `serviceId`, `commanderId`, text search, sort options, saved views.
- **Impact:** **Release friction** for any org with more than a few incidents; undermines commander workflow.
- **Priority:** **High-value** — recommended Phase 9 focus.

### Notifications and escalation

- **Status:** **Not implemented** (Phases 10–11 in plan).
- **Evidence:** No notification packages; Phase 7 design mentions future hook only.
- **Gaps:** No email/Slack/pager; no escalation policies; no on-call.
- **Impact:** Platform is record-keeping, not alerting — acceptable if positioned as coordination tool.
- **Priority:** High-value later; depends on triggers and channels; larger than discovery.

### Reliability, observability, and failure handling

- **Status:** Actuator health; structured `ApiErrorResponse`; transactional incident+event writes.
- **Evidence:** `application.yml` `management.endpoints`; `GlobalExceptionHandler`; `IncidentServiceEventRollbackIntegrationTest`.
- **Gaps:** No metrics/tracing; no structured correlation IDs; Redis unused for resilience patterns.
- **Priority:** Optional until production hardening (Phase 17).

### API consistency, validation, and error contracts

- **Status:** Consistent pagination envelopes; 409 domain codes documented in [api.md](api.md).
- **Evidence:** Postmortem and comment APIs mirror incident patterns.
- **Gaps:** Incident list API does not match Phase 5 filter contract; some design docs outdated.
- **Priority:** High-value to close filter contract gap.

### Security and authorization

- **Status:** JWT + refresh rotation; org RBAC on domain APIs; CSRF disabled (stateless API — ADR-009).
- **Evidence:** `SecurityConfig.java`, `@PreAuthorize` on user admin routes, org role checks in services.
- **Gaps:** No rate limiting on auth; no security headers documentation; bootstrap admin env optional.
- **Priority:** Rate limiting — incremental hardening; not blocking discovery work.

### Frontend usability and accessibility

- **Status:** Usable flows; some a11y on comments/timeline/postmortem (`aria-labelledby`, `role="status"`).
- **Evidence:** `incident-comments.tsx`, `incident-timeline.tsx`, `incident-postmortem-section.tsx`.
- **Gaps:** No systematic a11y audit; incident list table not optimized for keyboard/filter UX.
- **Priority:** Incremental with discovery UI.

### Testing quality and CI reliability

- **Status:** Strong **local** backend coverage; E2E breadth good when API+DB running.
- **Evidence:** development-plan Phase 8 verification counts; no CI workflow.
- **Gaps:** E2E not self-contained in Playwright `webServer` (backend not started); no PR gate.
- **Impact:** Regressions rely on manual runs.
- **Priority:** **Engineering blocker** for team scale — should land early in Phase 9 calendar.

### Local development, deployment, and production readiness

- **Status:** Documented Compose + `.env.example` templates; BootRun + `npm run dev`.
- **Evidence:** README (partially stale), `docker-compose.yml`, `backend/.env.example`.
- **Gaps:** No containerized app deploy; no K8s/Terraform; JWT secret required manually.
- **Priority:** Optional until Phase 17.

### Documentation and maintainability

- **Status:** Rich phase design docs; api/database/architecture updated for Phase 8.
- **Gaps:** README, Phase 5/8 design footers, Phase 5 status in development-plan.
- **Priority:** High-value **low-effort** doc pass (can parallel Step 0).

---

## 4. Ranked candidate initiatives

Comparison uses: **user value**, **core purpose fit**, **leverage**, **effort** (S/M/L/XL), **dependencies**, **acceptance criteria**, **rank rationale**.

| Rank | Initiative | User value | Effort | Dependencies | Key acceptance criteria | Rationale |
|------|------------|------------|--------|--------------|-------------------------|-----------|
| 1 | **Incident operational discovery** (filters, `active`, search, commander/reporter views) | High | M | Incidents API, existing DB indexes | `GET /incidents` supports Phase 5 minimum filters + UI; tests for combinations and tenant isolation | Closes largest ops gap; indexes already exist; no new infra |
| 2 | **CI pipeline** (backend test + frontend lint/typecheck/unit; optional E2E job) | Medium (team) | S–M | Docker for Testcontainers | PR workflow runs `./gradlew test` and frontend checks green | No automation today; protects Phases 5–8 investment |
| 3 | **Postmortem structured follow-up actions** | Medium–High | M–L | Phase 8 postmortems | CRUD action items linked to postmortem; RBAC; no mini-Jira | Natural learning-loop extension; deferred explicitly in Phase 8 |
| 4 | **Notification foundation** (in-app or email stub + domain events) | High | L | Stable incident/comment triggers | At least one channel; idempotent dispatch; tests | Unlocks mentions and on-call later; larger scope |
| 5 | **On-call schedules** (original plan Phase 9) | High | XL | Teams, users, time zones | Rotation CRUD; who is on-call now API | Core long-term; weak without notifications and discovery |
| 6 | **Escalation policies** | High | XL | On-call, notifications | Policy engine + tests | Plan Phase 10; depends on 5 and 4 |
| 7 | **Org member invites (email/link)** | Medium | M | Auth, email provider | Invite token flow; no UUID paste for admins | UX improvement; needs external mail or dev stub |
| 8 | **Dashboard / metrics** | Medium | L | Incident filters, aggregates | Org incident counts by status/severity | Plan Phase 13; better after filters |
| 9 | **Deployments correlation** | Medium | L | Incidents, external CI | Link deploy events to incidents | Plan Phase 12 |
| 10 | **Webhooks / monitoring ingest** | Medium | L | Auth, incident create API | Signed webhook → incident | Plan Phase 15 |

---

## 5. Recommended first initiative

### Incident operational discovery

**User problem:** Responders and commanders cannot quickly find open work, triage by severity or service, or see incidents they own when the org incident volume grows.

**Why now:** Create/detail/timeline/comments/postmortem are in place, but the **list** is the hub — it still behaves like an MVP dump sorted by recency. Phase 5 design promised filters that are absent in `IncidentController` and the incidents page.

**Smallest useful scope (MVP):**

1. **API query parameters** on `GET .../incidents` (align with [phase-5-incident-design.md](phase-5-incident-design.md)):
   - `active` (boolean) — exclude `RESOLVED` and `CANCELLED` when true
   - `status` (optional enum, repeatable or comma-separated — pick one style and document)
   - `severity` (optional)
   - `serviceId` (optional UUID)
   - `commanderId` (optional UUID; include preset `me` via caller id in service layer)
   - `search` (optional) — case-insensitive match on `title` and `INC-{n}` / `incident_number` (see [phase-9-incident-discovery-design.md](phase-9-incident-discovery-design.md) §5.5)
2. **Default list behavior:** document whether default remains “all” or switches to `active=true` (product choice; recommend **active default in UI** with “show all” toggle to avoid breaking API clients).
3. **Frontend:** filter bar on org incidents page; sync filters to URL search params; TanStack Query keys include filter object.
4. **Tests:** repository/service integration for filter combinations; controller tests for 403 isolation; Playwright smoke for filter + pagination.

**Out of scope for this initiative:**

- Saved views per user (needs persistence)
- Full-text search on description (optional later)
- New incident fields or assignment types
- Notifications on filter match
- Cross-org search

**Backend impact:** `IncidentRepository` query method(s), `IncidentService.listIncidents`, `IncidentController` params, possible `IncidentListFilter` record; **no migration required** if using existing columns/indexes.

**Frontend impact:** `listIncidents` API helper, incidents page, types.

**Risks:** Filter Cartesian product testing; URL param validation; performance acceptable with existing indexes until very large tenants (then composite indexes or search service).

**Why before on-call:** On-call answers “who gets paged”; discovery answers “what is open and who owns it” — immediate value with lower risk and no calendar/rotation model.

---

## 6. Proposed Phase 9 implementation steps (discovery initiative)

| Step | Objective | Scope / exclusions | Areas | Tests | Acceptance criteria | Depends on |
|------|-----------|-------------------|-------|-------|---------------------|------------|
| **0** | Design and plan sign-off | This roadmap + `phase-9-incident-discovery-design.md` (to author in Step 0) | `docs/` | Review | Filter matrix, API params, UI defaults documented | — |
| **1** | Backend list filter model | `IncidentListFilter`, validation, service wiring; no new tables | `incident/` | Unit tests for filter parsing and `active` logic | Invalid params → 400/409 consistent with platform | Step 0 |
| **2** | Persistence queries | JPA/spec or `@Query` with org scope + optional predicates | `IncidentRepository` | Integration tests: each filter + combined filters + 403 | Results match predicates; pagination stable sort (`createdAt` desc, tie-break `id`) | Step 1 |
| **3** | REST API + docs | Extend `GET /incidents`; update [api.md](api.md) | `IncidentController` | `IncidentControllerIntegrationTest` extended | Documented params match behavior | Step 2 |
| **4** | Frontend API + types | `listIncidents(orgId, { page, size, ...filters })` | `lib/api/incidents.ts`, `types/incident.ts` | Vitest if helpers added | Typecheck pass | Step 3 |
| **5** | Incidents list UI | Filter controls, URL sync, loading/empty states | `incidents/page.tsx` | Manual | User can filter active SEV1 for a service; refresh preserves filters | Step 4 |
| **6** | E2E | Playwright: create incidents with varied status; assert list filters | `tests/incidents.spec.ts` | E2E pass with API running | At least one filter journey green | Step 5 |
| **7** | Doc hygiene (parallel-friendly) | README, Phase 5 status, stale design headers | `README.md`, `development-plan.md` | N/A | Docs match Phases 1–8 | — |
| **8** | CI (parallel-friendly) | GitHub Actions: backend test, frontend lint/typecheck/unit | `.github/workflows/` | Workflow green on PR | Required checks documented in README | — |
| **9** | Sign-off | Full local verification per project conventions | — | `./gradlew test`, `npm run lint`, `typecheck`, `test:unit`, Playwright | development-plan Phase 9 section updated | Steps 1–6 (product); 7–8 (engineering) |

**Completion gate for Phase 9 (discovery):** Phase 5 “core filters” checklist satisfied in code and api.md; E2E covers filter path; no regression in existing incident/postmortem tests.

---

## 7. Engineering risks and priorities

### Should block or precede large feature work

| Risk | Evidence | Mitigation |
|------|----------|------------|
| **No CI** | No workflow files in repo | Add minimal GitHub Actions; document required checks |
| **E2E requires manual API** | `e2e-api.ts` calls `localhost:8080`; Playwright only starts Next dev server | CI job: start Compose Postgres + bootRun (or test profile); document local `test:e2e` prerequisites |
| **Documentation drift** | README, Phase 5/8 design headers | Doc pass in Step 7 |

### Address incrementally (do not block discovery)

| Risk | Notes |
|------|-------|
| CSRF disabled | Acceptable for bearer-token API; revisit for cookie-only sessions in production hardening |
| Redis unused | No action until caching/rate limit phase |
| JWT secret management | `.env.example` only; production secret store out of scope |
| No rate limiting on `/auth/*` | Add in Phase 17 or with Redis features |
| Global vs org roles | Documented; ENGINEER global role rarely used in org flows |

---

## 8. Acceptance criteria and completion gates (Phase 9 program)

**Product (discovery):**

- Organization members can filter incidents by status, severity, service, active set, and commander (including “assigned to me”).
- Filter state is shareable via URL (or documented alternative).
- API and UI stay tenant-scoped; outsider receives 403.
- Automated tests cover new backend behavior and at least one E2E filter flow.

**Engineering:**

- CI runs backend tests and frontend static checks on every PR.
- README accurately describes implemented phases and how to run full E2E.

**Explicit non-goals (Phase 9 program):**

- On-call rotations, escalation policies, outbound notifications
- Webhooks, deployment correlation, metrics dashboards
- Structured postmortem action items (candidate Phase 10)
- Reopening resolved incidents
- Markdown, attachments, @mentions
- Production Kubernetes/Terraform deployment
- Changing organization role model or global `User.role` semantics

---

## 9. Open questions (not answerable from repository)

1. **Default list semantics:** Should the API default to `active=true` or remain unfiltered for backward compatibility?
2. **Search depth:** Is title + incident number sufficient, or is description search required for launch?
3. **Saved views:** Per-user saved filters — needed in next quarter or defer?
4. **On-call vendor:** Build in-house rotations only, or integrate PagerDuty/Opsgenie later (affects Phase 10+ design)?
5. **Hosting target:** Single-tenant SaaS vs self-hosted — drives production hardening order.
6. **Email provider:** Required before member invites or any notification work?

---

## 10. Follow-on phases (after discovery)

Suggested reorder relative to original [development-plan.md](development-plan.md) **after** Phase 9 discovery + CI:

1. **Postmortem follow-up actions** (structured items from `correctiveActions` text)
2. **On-call schedules** (original Phase 9)
3. **Escalation policies** (Phase 10)
4. **Notifications** (Phase 11)
5. **Deployments, metrics, audit, webhooks, Redis/real-time, production hardening** (Phases 12–17)

Update the development plan phase table when the team adopts this sequencing — without marking implementation complete until steps land.

---

## References

- [development-plan.md](development-plan.md)
- [architecture.md](architecture.md)
- [api.md](api.md)
- [database.md](database.md)
- [phase-5-incident-design.md](phase-5-incident-design.md)
- [phase-8-postmortems-design.md](phase-8-postmortems-design.md)
- `backend/.../IncidentController.java`, `IncidentRepository.java`
- `frontend/src/app/(app)/organizations/[organizationId]/incidents/page.tsx`
