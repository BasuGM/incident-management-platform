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

Collaboration during active incidents.

## Phase 7: Postmortems

Structured follow-up and learning loops.

## Phase 8: On-call schedules

Rotations and coverage.

## Phase 9: Escalation policies

Automated escalation paths.

## Phase 10: Notifications

Email, chat, and push channels.

## Phase 11: Deployments

Change events correlated with incidents.

## Phase 12: Dashboard / metrics

Operational visibility and reporting.

## Phase 13: Audit logs

Immutable activity history.

## Phase 14: Webhooks / integrations

External monitoring and tooling.

## Phase 15: Redis / WebSockets / advanced infrastructure

Caching, rate limits, real-time updates, and async processing.

## Phase 16: Production hardening

Security review, observability, performance, and deployment automation.
