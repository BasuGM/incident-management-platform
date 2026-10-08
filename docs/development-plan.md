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

Structured follow-up and learning loops.

## Phase 9: On-call schedules

Rotations and coverage.

## Phase 10: Escalation policies

Automated escalation paths.

## Phase 11: Notifications

Email, chat, and push channels.

## Phase 12: Deployments

Change events correlated with incidents.

## Phase 13: Dashboard / metrics

Operational visibility and reporting.

## Phase 14: Audit logs

Immutable activity history.

## Phase 15: Webhooks / integrations

External monitoring and tooling.

## Phase 16: Redis / WebSockets / advanced infrastructure

Caching, rate limits, real-time updates, and async processing.

## Phase 17: Production hardening

Security review, observability, performance, and deployment automation.
