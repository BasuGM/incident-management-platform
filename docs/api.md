# API conventions

## Base path

All public REST endpoints use:

```text
/api/v1/*
```

## Authentication

Stateless JWT access tokens are sent as:

```text
Authorization: Bearer <access-token>
```

Refresh tokens are issued as HttpOnly cookies on auth endpoints (`Path=/api/v1/auth`) and may also appear in JSON responses for non-browser clients. Browser clients should rely on the cookie for refresh and keep access tokens in memory only.

### `POST /api/v1/auth/register`

Creates a user with the default `ENGINEER` role.

### `POST /api/v1/auth/login`

Authenticates a user and returns access/refresh tokens plus user profile.

### `POST /api/v1/auth/refresh`

Rotates the refresh token and returns a new access token. Accepts the refresh token from the HttpOnly cookie or JSON body.

### `POST /api/v1/auth/logout`

Revokes the refresh token and clears the refresh cookie.

## Users

### `GET /api/v1/users/me`

Returns the authenticated user (requires authentication).

### `GET /api/v1/users`

Lists users (`ADMIN` only).

### `GET /api/v1/users/{userId}`

`ADMIN` for any user; `ENGINEER` / `VIEWER` for their own profile.

### `PATCH /api/v1/users/{userId}`

Updates a user (`ADMIN` only).

## Organizations (tenant-scoped)

Organization roles: `OWNER`, `ADMIN`, `MEMBER`, `VIEWER` (membership role, separate from global `User.role`).

### `GET /api/v1/organizations`

Lists organizations where the current user is a member.

### `POST /api/v1/organizations`

Creates an organization; creator becomes `OWNER`.

### `GET /api/v1/organizations/{organizationId}`

Returns organization details for members only (`403` for non-members).

### `PATCH /api/v1/organizations/{organizationId}`

Updates organization metadata (`OWNER` only).

### `GET|POST /api/v1/organizations/{organizationId}/members`

List/add members (`OWNER`/`ADMIN` for writes).

### `PATCH|DELETE /api/v1/organizations/{organizationId}/members/{userId}`

Update/remove member roles (`OWNER`/`ADMIN` with owner safeguards).

### `GET|POST /api/v1/organizations/{organizationId}/teams`

List/create teams (`OWNER`/`ADMIN` for writes).

### `GET|PATCH|DELETE /api/v1/organizations/{organizationId}/teams/{teamId}`

Read/update/delete a team within the organization (`OWNER`/`ADMIN` for writes).

### `GET|POST /api/v1/organizations/{organizationId}/services`

List/create services in the organization. Any organization member may list; `OWNER`/`ADMIN` may create.

Create body: `name` (required), `slug` (required, organization slug rules, stored lowercase), optional `description`, optional `teamId` (must belong to the same organization).

### `GET|PATCH|DELETE /api/v1/organizations/{organizationId}/services/{serviceId}`

Read/update/delete a service (`OWNER`/`ADMIN` for writes). Cross-organization access returns `403`.

PATCH updates only fields present in the body (same semantics as teams). `teamId` is updated when provided; omitting `teamId` leaves the owning team unchanged.

### `GET|POST /api/v1/organizations/{organizationId}/incidents`

List/create incidents in the organization (authentication required).

**List:** any organization member. Query parameters: `page` (default `0`), `size` (default `20`, max `100`). Results are ordered by `createdAt` descending. Response shape:

```json
{
  "content": [ /* IncidentResponse */ ],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

**Create:** `OWNER`, `ADMIN`, and `MEMBER` may create; `VIEWER` receives `403`. The authenticated user is always the reporter (client cannot set `reporterId`). Create body: `title` (required, max 200), optional `description` (max 10000), `severity` (required: `SEV1`–`SEV4`), optional `serviceId`, optional `commanderId` (must be organization members when set). Returns `201` with `IncidentResponse`. Initial `status` is `OPEN`; `incidentNumber` and `displayId` (`INC-{number}`) are server-assigned.

### `GET|PATCH /api/v1/organizations/{organizationId}/incidents/{incidentId}`

Read/update a single incident. Any organization member may read. Updates require `OWNER`, `ADMIN`, or `MEMBER` (`VIEWER` → `403`). Cross-organization access returns `403`.

PATCH applies only fields present in the JSON body. `serviceId` and `commanderId` may be set to `null` to clear; omitting a field leaves it unchanged. Status transitions and cancellation rules are enforced in the service layer (`409` with codes such as `INVALID_INCIDENT_STATUS_TRANSITION`, `INCIDENT_NOT_EDITABLE`). Cancellation (`status: CANCELLED`) requires `OWNER` or `ADMIN`.

`IncidentResponse` includes: `id`, `organizationId`, `incidentNumber`, `displayId`, `title`, `description`, `severity`, `status`, `serviceId`, `serviceName`, `reporterId`, `reporterEmail`, `reporterFirstName`, `reporterLastName`, `commanderId`, `commanderEmail`, `commanderFirstName`, `commanderLastName`, `createdAt`, `updatedAt`, `acknowledgedAt`, `resolvedAt`, `cancelledAt`.

There is no `DELETE` incident endpoint in Phase 5.

### `GET|POST /api/v1/teams/{teamId}/members`

List/add team members (`OWNER`/`ADMIN` in parent organization). Team members must already belong to the organization.

### `DELETE /api/v1/teams/{teamId}/members/{userId}`

Remove a team member (`OWNER`/`ADMIN`).

## Health endpoint

### `GET /api/v1/health`

Returns a simple structured response indicating the API process is running.

Example response:

```json
{
  "status": "UP",
  "service": "incident-management-api"
}
```

## Error format

API errors use a consistent JSON envelope (see backend `ApiErrorResponse`):

```json
{
  "timestamp": "2026-09-29T12:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/example",
  "details": []
}
```

Common auth-related statuses:

| Status | Meaning |
|--------|---------|
| 400 | Validation error |
| 401 | Unauthenticated / invalid credentials / invalid token |
| 403 | Authenticated but forbidden |
| 404 | User not found |
| 409 | Duplicate email |
